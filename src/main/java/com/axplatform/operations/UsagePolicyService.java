package com.axplatform.operations;

import com.axplatform.common.*;
import com.axplatform.security.*;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class UsagePolicyService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final AccountSessionService sessions;
    private final Clock clock;
    private final JsonCodec json;
    private final long defaultBytes;

    public UsagePolicyService(
            JdbcTemplate db,
            TransactionTemplate tx,
            AccountSessionService sessions,
            Clock clock,
            JsonCodec json,
            Environment env) {
        this.db = db;
        this.tx = tx;
        this.sessions = sessions;
        this.clock = clock;
        this.json = json;
        defaultBytes = env.getProperty("ax.storage.default-quota-bytes", Long.class, 1073741824L);
        if (defaultBytes < 1) throw new IllegalArgumentException("storage quota");
    }

    public void current(SessionSubject s) {
        if (!sessions.current(s)) throw new Problem(401, "AUTH_REQUIRED", "다시 로그인해 주세요.");
    }

    public void admin(SessionSubject s) {
        current(s);
        if (!s.administrator()) throw new Problem(403, "ADMIN_REQUIRED", "관리자 전용 기능입니다.");
    }

    public void lock(long owner) {
        if (db.queryForList("SELECT id FROM ap_account WHERE id=? FOR UPDATE", owner).isEmpty())
            throw new Problem(404, "MEMBER_NOT_FOUND", "회원을 찾지 못했습니다.");
    }

    public Map<String, Object> policy(long owner) {
        var rows = db.queryForList("SELECT * FROM ap_usage_policy WHERE account_id=?", owner);
        return rows.isEmpty()
                ? new LinkedHashMap<>(
                        Map.of(
                                "account_id",
                                owner,
                                "storage_limit_bytes",
                                defaultBytes,
                                "ai_enabled",
                                true,
                                "monthly_token_limit",
                                0L,
                                "monthly_cost_limit",
                                BigDecimal.ZERO,
                                "version",
                                -1L))
                : rows.get(0);
    }

    public long bytes(long owner) {
        return db.queryForObject(
                "SELECT COALESCE(SUM(byte_size),0) FROM ap_asset WHERE owner_account_id=? AND"
                    + " storage_state<>'PURGED'",
                Long.class,
                owner);
    }

    public Map<String, Object> quota(SessionSubject s, long owner, boolean administrator) {
        if (administrator) admin(s);
        else {
            current(s);
            if (owner != s.accountId())
                throw new Problem(403, "OWNER_REQUIRED", "내 자료만 확인할 수 있습니다.");
        }
        var p = policy(owner);
        long used = bytes(owner), limit = Inputs.number(p, "storage_limit_bytes");
        return Map.of(
                "usedBytes",
                used,
                "limitBytes",
                limit,
                "availableBytes",
                Math.max(0, limit - used),
                "policy",
                p);
    }

    /** Caller holds an account row lock until the file metadata transaction commits. */
    public void admitFile(long owner, long size) {
        long limit = Inputs.number(policy(owner), "storage_limit_bytes"), used = bytes(owner);
        if (size < 1 || used > limit - size)
            throw new Problem(
                    409, "STORAGE_QUOTA", "보관 용량이 부족합니다. 내 자료의 휴지통에서 필요 없는 파일을 영구 삭제하세요.");
    }

    public record Change(
            long storageLimitBytes,
            boolean aiEnabled,
            long monthlyTokenLimit,
            BigDecimal monthlyCostLimit,
            long version,
            String reason) {}

    public Object change(SessionSubject s, long owner, Change c) {
        admin(s);
        String reason = Inputs.text(c.reason(), 500, "변경 사유");
        if (c.storageLimitBytes() < 1
                || c.storageLimitBytes() > 100000000000000L
                || c.monthlyTokenLimit() < 0
                || c.monthlyCostLimit() == null
                || c.monthlyCostLimit().signum() < 0
                || c.monthlyCostLimit().compareTo(new BigDecimal("1000000000")) > 0)
            throw Problem.invalid("용량·토큰·USD 한도를 확인하세요.");
        return tx.execute(
                t -> {
                    admin(s);
                    lock(owner);
                    var before = policy(owner);
                    if (Inputs.number(before, "version") != c.version())
                        throw Problem.conflict("다른 관리자가 한도를 변경했습니다.");
                    if (c.version() < 0)
                        db.update(
                                "INSERT INTO"
                                    + " ap_usage_policy(account_id,storage_limit_bytes,ai_enabled,monthly_token_limit,monthly_cost_limit,updated_at)"
                                    + " VALUES(?,?,?,?,?,?)",
                                owner,
                                c.storageLimitBytes(),
                                c.aiEnabled(),
                                c.monthlyTokenLimit(),
                                c.monthlyCostLimit(),
                                LocalDateTime.now(clock));
                    else
                        db.update(
                                "UPDATE ap_usage_policy SET"
                                    + " storage_limit_bytes=?,ai_enabled=?,monthly_token_limit=?,monthly_cost_limit=?,version=version+1,updated_at=?"
                                    + " WHERE account_id=?",
                                c.storageLimitBytes(),
                                c.aiEnabled(),
                                c.monthlyTokenLimit(),
                                c.monthlyCostLimit(),
                                LocalDateTime.now(clock),
                                owner);
                    var after = policy(owner);
                    audit(s, "USAGE_POLICY", String.valueOf(owner), reason, before, after);
                    return after;
                });
    }

    public void audit(
            SessionSubject s,
            String action,
            String target,
            String reason,
            Object before,
            Object after) {
        db.update(
                "INSERT INTO"
                    + " ap_operations_audit(actor_account_id,target_id,action_code,reason,before_json,after_json,created_at)"
                    + " VALUES(?,?,?,?,?,?,?)",
                s.accountId(),
                target,
                action,
                reason,
                json.write(before),
                json.write(after),
                LocalDateTime.now(clock));
    }
}
