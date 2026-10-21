package com.axplatform.operations;

import com.axplatform.common.*;
import com.axplatform.security.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class AiMeteringService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final UsagePolicyService limits;
    private final Clock clock;

    public AiMeteringService(
            JdbcTemplate db, TransactionTemplate tx, UsagePolicyService limits, Clock clock) {
        this.db = db;
        this.tx = tx;
        this.limits = limits;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static BigDecimal decimal(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private static boolean enabled(Object o) {
        return Boolean.TRUE.equals(o) || "1".equals(String.valueOf(o));
    }

    public void check(SessionSubject s) {
        limits.current(s);
        if (!enabled(
                        db.queryForMap("SELECT * FROM ap_operation_settings WHERE id=1")
                                .get("ai_enabled"))
                || !enabled(limits.policy(s.accountId()).get("ai_enabled")))
            throw new Problem(403, "AI_PAUSED", "AI 사용이 일시 중지되었습니다. 수동 편집은 계속 사용할 수 있습니다.");
    }

    public String reserve(
            SessionSubject s, String run, int round, String model, long input, int output) {
        if (input < 0 || input > 10000000 || output < 1)
            throw Problem.invalid("입력 토큰 사전 계산을 확인하지 못했습니다.");
        return tx.execute(
                t -> {
                    db.queryForList("SELECT id FROM ap_operation_settings WHERE id=1 FOR UPDATE");
                    limits.lock(s.accountId());
                    check(s);
                    var r = db.queryForMap("SELECT * FROM ap_ai_run WHERE id=?", run);
                    if (Inputs.number(r, "account_id") != s.accountId()
                            || !"RUNNING".equals(r.get("status")))
                        throw Problem.conflict("AI 작업이 중단되었습니다.");
                    var p = limits.policy(s.accountId());
                    var prices =
                            db.queryForList(
                                    "SELECT * FROM ap_model_price WHERE model=? ORDER BY created_at"
                                        + " DESC,id DESC LIMIT 1",
                                    model);
                    var price = prices.isEmpty() ? null : prices.get(0);
                    long tokens = Math.addExact(input, output);
                    BigDecimal cost =
                            price == null
                                    ? null
                                    : decimal(price.get("input_rate"))
                                            .max(decimal(price.get("cached_rate")))
                                            .max(decimal(price.get("cache_write_rate")))
                                            .multiply(BigDecimal.valueOf(input))
                                            .add(
                                                    decimal(price.get("output_rate"))
                                                            .multiply(BigDecimal.valueOf(output)))
                                            .divide(
                                                    BigDecimal.valueOf(1000000),
                                                    6,
                                                    RoundingMode.CEILING);
                    LocalDateTime month =
                            YearMonth.now(clock.withZone(ZoneId.of("Asia/Seoul")))
                                    .atDay(1)
                                    .atStartOfDay(ZoneId.of("Asia/Seoul"))
                                    .withZoneSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                    var used =
                            db.queryForMap(
                                    "SELECT COALESCE(SUM(CASE WHEN state IN"
                                        + " ('MEASURED','RECONCILED') THEN"
                                        + " COALESCE(input_tokens,0)+COALESCE(output_tokens,0) ELSE"
                                        + " reserved_tokens END),0) AS tokens, COALESCE(SUM(CASE"
                                        + " WHEN state IN ('MEASURED','RECONCILED') THEN"
                                        + " estimated_cost ELSE reserved_cost END),0) AS cost,"
                                        + " SUM(CASE WHEN (state IN ('MEASURED','RECONCILED') AND"
                                        + " estimated_cost IS NULL) OR (state NOT IN"
                                        + " ('MEASURED','RECONCILED') AND reserved_cost IS NULL)"
                                        + " THEN 1 ELSE 0 END) AS unpriced FROM ap_ai_call WHERE"
                                        + " account_id=? AND (created_at>=? OR state IN"
                                        + " ('STARTED','UNKNOWN'))",
                                    s.accountId(),
                                    month);
                    long tokenLimit = Inputs.number(p, "monthly_token_limit");
                    if (tokenLimit > 0 && Inputs.number(used, "tokens") > tokenLimit - tokens)
                        throw new Problem(
                                429,
                                "AI_TOKEN_BUDGET",
                                "월 토큰 한도에 도달했거나 이번 호출의 최대 사용량을 예약할 여유가 없습니다.");
                    BigDecimal budget = decimal(p.get("monthly_cost_limit"));
                    if (budget.signum() > 0
                            && (cost == null || decimal(used.get("unpriced")).signum() > 0))
                        throw new Problem(
                                409,
                                "AI_PRICE_REQUIRED",
                                "비용 한도를 적용하려면 모델 단가와 미집계 비용을 먼저 확인해야 합니다.");
                    if (budget.signum() > 0
                            && decimal(used.get("cost")).add(cost).compareTo(budget) > 0)
                        throw new Problem(
                                429, "AI_COST_BUDGET", "이번 호출의 최대 비용을 예약하면 월 비용 한도를 넘습니다.");
                    String id = UUID.randomUUID().toString();
                    String workflow =
                            db.queryForObject(
                                    "SELECT workflow FROM ap_work_draft WHERE id=?",
                                    String.class,
                                    r.get("draft_id"));
                    db.update(
                            "INSERT INTO"
                                + " ap_ai_call(id,run_id,account_id,business_scope_id,project_id,workflow,model,round_no,state,reserved_tokens,reserved_cost,price_id,created_at,updated_at)"
                                + " VALUES(?,?,?,?,?,?,?,?,'STARTED',?,?,?,?,?)",
                            id,
                            run,
                            s.accountId(),
                            r.get("business_scope_id"),
                            r.get("draft_id"),
                            workflow,
                            model,
                            round,
                            tokens,
                            cost,
                            price == null ? null : price.get("id"),
                            now(),
                            now());
                    return id;
                });
    }

    private static long token(Object o) {
        if (!(o instanceof Number n) || n.longValue() < 0)
            throw Problem.invalid("AI 사용량 응답을 확인하지 못했습니다.");
        return n.longValue();
    }

    @SuppressWarnings("unchecked")
    public void measured(String id, Map<?, ?> usage, long duration) {
        if (usage == null) {
            unknown(id, "USAGE_MISSING", duration);
            return;
        }
        try {
            long in = token(usage.get("input_tokens")), out = token(usage.get("output_tokens"));
            Map<?, ?> detail =
                    usage.get("input_tokens_details") instanceof Map<?, ?> m ? m : Map.of();
            Map<?, ?> od = usage.get("output_tokens_details") instanceof Map<?, ?> m ? m : Map.of();
            long
                    cached =
                            token(
                                    detail.containsKey("cached_tokens")
                                            ? detail.get("cached_tokens")
                                            : 0),
                    write =
                            token(
                                    detail.containsKey("cache_write_tokens")
                                            ? detail.get("cache_write_tokens")
                                            : 0),
                    reasoning =
                            token(
                                    od.containsKey("reasoning_tokens")
                                            ? od.get("reasoning_tokens")
                                            : 0);
            if (cached + write > in || reasoning > out)
                throw Problem.invalid("사용량 상세 합계를 확인하지 못했습니다.");
            tx.executeWithoutResult(
                    t -> {
                        var r = db.queryForMap("SELECT * FROM ap_ai_call WHERE id=?", id);
                        limits.lock(Inputs.number(r, "account_id"));
                        r = db.queryForMap("SELECT * FROM ap_ai_call WHERE id=?", id);
                        if (!Set.of("STARTED", "UNKNOWN").contains(r.get("state"))) return;
                        BigDecimal cost = null;
                        if (r.get("price_id") != null) {
                            var p =
                                    db.queryForMap(
                                            "SELECT * FROM ap_model_price WHERE id=?",
                                            r.get("price_id"));
                            cost =
                                    decimal(p.get("input_rate"))
                                            .multiply(BigDecimal.valueOf(in - cached - write))
                                            .add(
                                                    decimal(p.get("cached_rate"))
                                                            .multiply(BigDecimal.valueOf(cached)))
                                            .add(
                                                    decimal(p.get("cache_write_rate"))
                                                            .multiply(BigDecimal.valueOf(write)))
                                            .add(
                                                    decimal(p.get("output_rate"))
                                                            .multiply(BigDecimal.valueOf(out)))
                                            .divide(
                                                    BigDecimal.valueOf(1000000),
                                                    6,
                                                    RoundingMode.CEILING);
                        }
                        db.update(
                                "UPDATE ap_ai_call SET"
                                    + " state='MEASURED',input_tokens=?,cached_tokens=?,cache_write_tokens=?,output_tokens=?,reasoning_tokens=?,estimated_cost=?,duration_ms=?,safe_error_code=?,updated_at=?"
                                    + " WHERE id=?",
                                in,
                                cached,
                                write,
                                out,
                                reasoning,
                                cost,
                                duration,
                                in + out > Inputs.number(r, "reserved_tokens")
                                        ? "RESERVATION_EXCEEDED"
                                        : null,
                                now(),
                                id);
                        db.update(
                                "UPDATE ap_ai_run SET input_tokens=(SELECT"
                                    + " COALESCE(SUM(input_tokens),0) FROM ap_ai_call WHERE"
                                    + " run_id=?),output_tokens=(SELECT"
                                    + " COALESCE(SUM(output_tokens),0) FROM ap_ai_call WHERE"
                                    + " run_id=?) WHERE id=?",
                                r.get("run_id"),
                                r.get("run_id"),
                                r.get("run_id"));
                    });
        } catch (Problem invalid) {
            unknown(id, "USAGE_INVALID", duration);
        }
    }

    public void unknown(String id, String error, long duration) {
        db.update(
                "UPDATE ap_ai_call SET state='UNKNOWN',safe_error_code=?,duration_ms=?,updated_at=?"
                    + " WHERE id=? AND state='STARTED'",
                error != null && error.matches("[A-Z0-9_]{1,80}") ? error : "AI_CALL_FAILED",
                Math.max(0, duration),
                now(),
                id);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void abandoned() {
        db.update(
                "UPDATE ap_ai_call SET"
                    + " state='UNKNOWN',safe_error_code='WORKER_INTERRUPTED',updated_at=? WHERE"
                    + " state='STARTED' AND run_id IN (SELECT id FROM ap_ai_run WHERE status NOT IN"
                    + " ('QUEUED','RUNNING') OR lease_until<?)",
                now(),
                now());
    }

    public record Price(
            String model,
            BigDecimal input,
            BigDecimal cached,
            BigDecimal cacheWrite,
            BigDecimal output,
            String reason) {}

    public Object price(SessionSubject s, Price p) {
        limits.admin(s);
        String model = Inputs.text(p.model(), 100, "모델"),
                reason = Inputs.text(p.reason(), 500, "단가 근거");
        for (BigDecimal v : Arrays.asList(p.input(), p.cached(), p.cacheWrite(), p.output()))
            if (v == null || v.signum() < 0 || v.compareTo(new BigDecimal("100000")) > 0)
                throw Problem.invalid("100만 토큰당 USD 단가를 확인하세요.");
        return tx.execute(
                t -> {
                    limits.admin(s);
                    String id = UUID.randomUUID().toString();
                    db.update(
                            "INSERT INTO"
                                + " ap_model_price(id,model,input_rate,cached_rate,cache_write_rate,output_rate,created_at,actor_account_id)"
                                + " VALUES(?,?,?,?,?,?,?,?)",
                            id,
                            model,
                            p.input(),
                            p.cached(),
                            p.cacheWrite(),
                            p.output(),
                            now(),
                            s.accountId());
                    limits.audit(s, "MODEL_PRICE", id, reason, Map.of(), p);
                    return Map.of("id", id);
                });
    }

    public record Reconcile(long inputTokens, long outputTokens, BigDecimal cost, String reason) {}

    public Object reconcile(SessionSubject s, String id, Reconcile input) {
        limits.admin(s);
        String reason = Inputs.text(input.reason(), 500, "제공사 확인 근거");
        if (input.inputTokens() < 0
                || input.outputTokens() < 0
                || input.inputTokens() > 100000000
                || input.outputTokens() > 100000000
                || input.cost() == null
                || input.cost().signum() < 0
                || input.cost().compareTo(new BigDecimal("1000000000")) > 0)
            throw Problem.invalid("확인된 토큰·USD 비용을 입력하세요.");
        return tx.execute(
                t -> {
                    limits.admin(s);
                    var before = db.queryForMap("SELECT * FROM ap_ai_call WHERE id=?", id);
                    limits.lock(Inputs.number(before, "account_id"));
                    var r = db.queryForMap("SELECT * FROM ap_ai_call WHERE id=?", id);
                    if (!"UNKNOWN".equals(r.get("state"))
                            && !("MEASURED".equals(r.get("state"))
                                    && r.get("estimated_cost") == null))
                        throw Problem.conflict("미확인 또는 단가 미설정 호출만 정산할 수 있습니다.");
                    if ("MEASURED".equals(r.get("state"))
                            && (input.inputTokens() != Inputs.number(r, "input_tokens")
                                    || input.outputTokens() != Inputs.number(r, "output_tokens")))
                        throw Problem.invalid("이미 확인된 토큰 합계는 변경할 수 없습니다.");
                    db.update(
                            "UPDATE ap_ai_call SET"
                                + " state='RECONCILED',input_tokens=?,output_tokens=?,estimated_cost=?,updated_at=?"
                                + " WHERE id=?",
                            input.inputTokens(),
                            input.outputTokens(),
                            input.cost(),
                            now(),
                            id);
                    limits.audit(s, "AI_RECONCILE", id, reason, r, input);
                    db.update(
                            "UPDATE ap_ai_run SET input_tokens=(SELECT"
                                + " COALESCE(SUM(input_tokens),0) FROM ap_ai_call WHERE"
                                + " run_id=?),output_tokens=(SELECT COALESCE(SUM(output_tokens),0)"
                                + " FROM ap_ai_call WHERE run_id=?) WHERE id=?",
                            r.get("run_id"),
                            r.get("run_id"),
                            r.get("run_id"));
                    return Map.of("status", "reconciled");
                });
    }
}
