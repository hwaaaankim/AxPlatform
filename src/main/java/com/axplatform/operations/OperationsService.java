package com.axplatform.operations;

import com.axplatform.common.*;
import com.axplatform.security.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;

@Service
public class OperationsService {
    private final JdbcTemplate db;
    private final UsagePolicyService limits;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ActivityRecorder activity;

    public OperationsService(
            JdbcTemplate db,
            UsagePolicyService limits,
            TransactionTemplate tx,
            Clock clock,
            ActivityRecorder activity) {
        this.db = db;
        this.limits = limits;
        this.tx = tx;
        this.clock = clock;
        this.activity = activity;
    }

    public Map<String, Object> report(
            SessionSubject s,
            boolean admin,
            String from,
            String to,
            Long account,
            String workflow) {
        if (admin) limits.admin(s);
        else limits.current(s);
        LocalDate a, b;
        try {
            a = LocalDate.parse(from);
            b = LocalDate.parse(to);
        } catch (Exception e) {
            throw Problem.invalid("조회 날짜를 선택하세요.");
        }
        if (a.isAfter(b) || a.plusDays(365).isBefore(b))
            throw Problem.invalid("조회 기간은 시작일부터 최대 366일입니다.");
        var start =
                a.atStartOfDay(ZoneId.of("Asia/Seoul"))
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        var end =
                b.plusDays(1)
                        .atStartOfDay(ZoneId.of("Asia/Seoul"))
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        List<Object> args = new ArrayList<>(List.of(start, end));
        String filter = "created_at>=? AND created_at<?",
                who =
                        admin
                                ? account == null ? null : String.valueOf(account)
                                : String.valueOf(s.accountId());
        String events = filter, calls = filter;
        if (who != null) {
            events += " AND actor_account_id=?";
            calls += " AND account_id=?";
            args.add(Long.parseLong(who));
        }
        if (workflow != null && !workflow.isBlank()) {
            if (!com.axplatform.storage.ManagedStorage.WORKFLOWS.contains(workflow))
                throw Problem.invalid("업무를 선택하세요.");
            events += " AND workflow=?";
            calls += " AND workflow=?";
            args.add(workflow);
        }
        Object[] values = args.toArray();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "activity",
                db.queryForMap(
                        "SELECT COUNT(*) AS actions,COUNT(DISTINCT actor_account_id) AS"
                            + " users,COALESCE(SUM(CASE WHEN outcome='FAILURE' THEN 1 ELSE 0"
                            + " END),0) AS failures FROM ap_activity_event WHERE "
                                + events,
                        values));
        out.put(
                "daily",
                db.queryForList(
                        "SELECT CAST(TIMESTAMPADD(HOUR,9,created_at) AS DATE) AS"
                            + " activity_day,COUNT(*) AS actions FROM ap_activity_event WHERE "
                                + events
                                + " GROUP BY CAST(TIMESTAMPADD(HOUR,9,created_at) AS DATE) ORDER BY"
                                + " activity_day",
                        values));
        out.put(
                "features",
                db.queryForList(
                        "SELECT action_code,workflow,COUNT(*) AS actions,SUM(CASE WHEN"
                            + " outcome='FAILURE' THEN 1 ELSE 0 END) AS failures,AVG(duration_ms)"
                            + " AS average_ms FROM ap_activity_event WHERE "
                                + events
                                + " GROUP BY action_code,workflow ORDER BY actions DESC LIMIT 50",
                        values));
        out.put(
                "failures",
                db.queryForList(
                        "SELECT action_code,safe_error_code,COUNT(*) AS failures FROM"
                            + " ap_activity_event WHERE "
                                + events
                                + " AND outcome='FAILURE' GROUP BY action_code,safe_error_code"
                                + " ORDER BY failures DESC LIMIT 30",
                        values));
        out.put(
                "ai",
                db.queryForMap(
                        "SELECT COUNT(*) AS calls,COALESCE(SUM(input_tokens),0) AS"
                            + " input_tokens,COALESCE(SUM(cached_tokens),0) AS"
                            + " cached_tokens,COALESCE(SUM(cache_write_tokens),0) AS"
                            + " cache_write_tokens,COALESCE(SUM(output_tokens),0) AS"
                            + " output_tokens,COALESCE(SUM(reasoning_tokens),0) AS"
                            + " reasoning_tokens,SUM(estimated_cost) AS estimated_cost,SUM(CASE"
                            + " WHEN state IN ('STARTED','UNKNOWN') THEN 1 ELSE 0 END) AS"
                            + " unknown_calls,SUM(CASE WHEN estimated_cost IS NULL THEN 1 ELSE 0"
                            + " END) AS unpriced_calls,COALESCE(SUM(CASE WHEN state IN"
                            + " ('STARTED','UNKNOWN') THEN reserved_tokens ELSE 0 END),0) AS"
                            + " reserved_tokens FROM ap_ai_call WHERE "
                                + calls,
                        values));
        out.put(
                "models",
                db.queryForList(
                        "SELECT model,workflow,COUNT(*) AS calls,SUM(input_tokens) AS"
                            + " input_tokens,SUM(output_tokens) AS"
                            + " output_tokens,SUM(estimated_cost) AS estimated_cost FROM ap_ai_call"
                            + " WHERE "
                                + calls
                                + " GROUP BY model,workflow ORDER BY calls DESC",
                        values));
        if (admin) {
            out.put(
                    "audit",
                    db.queryForList(
                            "SELECT actor_account_id,target_id,action_code,reason,created_at FROM"
                                + " ap_operations_audit WHERE created_at>=? AND created_at<? ORDER"
                                + " BY id DESC LIMIT 100",
                            start,
                            end));
            out.put(
                    "users",
                    db.queryForList(
                            "SELECT actor_account_id,COUNT(*) AS actions,SUM(CASE WHEN"
                                + " outcome='FAILURE' THEN 1 ELSE 0 END) AS failures FROM"
                                + " ap_activity_event WHERE "
                                    + events
                                    + " GROUP BY actor_account_id ORDER BY actions DESC LIMIT 50",
                            values));
            out.put(
                    "pending",
                    db.queryForList(
                            "SELECT"
                                + " id,account_id,model,state,input_tokens,output_tokens,reserved_tokens,reserved_cost,safe_error_code,created_at"
                                + " FROM ap_ai_call WHERE "
                                    + calls
                                    + " AND (state='UNKNOWN' OR (state='MEASURED' AND"
                                    + " estimated_cost IS NULL)) ORDER BY created_at DESC LIMIT"
                                    + " 100",
                            values));
            out.put("settings", db.queryForMap("SELECT * FROM ap_operation_settings WHERE id=1"));
            out.put(
                    "prices",
                    db.queryForList(
                            "SELECT * FROM ap_model_price ORDER BY created_at DESC,id DESC LIMIT"
                                + " 30"));
        }
        out.put("retentionDays", activity.retention());
        out.put("timezone", "Asia/Seoul");
        out.put("from", from);
        out.put("to", to);
        return out;
    }

    public record Global(boolean enabled, long version, String reason) {}

    public Object global(SessionSubject s, Global g) {
        limits.admin(s);
        String reason = Inputs.text(g.reason(), 500, "변경 사유");
        return tx.execute(
                t -> {
                    limits.admin(s);
                    var old =
                            db.queryForMap(
                                    "SELECT * FROM ap_operation_settings WHERE id=1 FOR UPDATE");
                    if (Inputs.number(old, "version") != g.version())
                        throw Problem.conflict("설정이 변경되었습니다.");
                    db.update(
                            "UPDATE ap_operation_settings SET"
                                + " ai_enabled=?,version=version+1,updated_at=? WHERE id=1",
                            g.enabled(),
                            LocalDateTime.now(clock));
                    limits.audit(
                            s,
                            "AI_GLOBAL_SWITCH",
                            "1",
                            reason,
                            old,
                            Map.of("enabled", g.enabled()));
                    return Map.of("status", "saved");
                });
    }
}
