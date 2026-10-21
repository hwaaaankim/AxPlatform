package com.axplatform.policy;

import com.axplatform.common.*;
import com.axplatform.security.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Policy changes and data commits use the same lock. No permissions cached in a login session. */
@Service
public class AccessService {
    private final com.axplatform.operations.ActivityRecorder activity;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final PolicyService policies;
    private final AccountSessionService sessions;
    private final Clock clock;

    public AccessService(
            JdbcTemplate db,
            TransactionTemplate tx,
            PolicyService policies,
            AccountSessionService sessions,
            Clock clock,
            com.axplatform.operations.ActivityRecorder activity) {
        this.activity = activity;
        this.db = db;
        this.tx = tx;
        this.policies = policies;
        this.sessions = sessions;
        this.clock = clock;
    }

    public String tier(long workspace) {
        var overrides =
                db.queryForList(
                        "SELECT tier_code FROM ap_workspace_tier_override WHERE workspace_id=? AND"
                            + " (expires_at IS NULL OR expires_at>?)",
                        String.class,
                        workspace,
                        LocalDateTime.now(clock));
        if (!overrides.isEmpty()) return overrides.get(0);
        String plan =
                db.queryForObject(
                        "SELECT plan_code FROM ap_workspace WHERE id=?", String.class, workspace);
        return switch (plan == null ? "FREE" : plan) {
            case "BASIC" -> "TIER1";
            case "PRO" -> "TIER2";
            case "MAX" -> "TIER3";
            default -> "GUEST";
        };
    }

    public void member(SessionSubject s, long workspace) {
        if (!sessions.current(s)) throw new Problem(401, "SESSION_EXPIRED", "다시 로그인해 주세요.");
        Long count =
                db.queryForObject(
                        "SELECT COUNT(*) FROM ap_workspace w JOIN ap_workspace_member m ON"
                            + " m.workspace_id=w.id WHERE w.id=? AND m.account_id=? AND"
                            + " w.owner_account_id=? AND w.kind=?",
                        Long.class,
                        workspace,
                        s.accountId(),
                        s.accountId(),
                        s.administrator() ? "ADMIN_TEST" : "CUSTOMER");
        if (count == null || count != 1)
            throw new Problem(404, "SCOPE_NOT_FOUND", "본인의 업무 공간을 찾지 못했습니다.");
    }

    public Map<String, Object> scope(SessionSubject s, long business, boolean allowArchived) {
        var rows =
                db.queryForList(
                        "SELECT b.*,w.kind FROM ap_business_scope b JOIN ap_workspace w ON"
                            + " w.id=b.workspace_id WHERE b.id=?",
                        business);
        if (rows.isEmpty()) throw new Problem(404, "SCOPE_NOT_FOUND", "사업을 찾지 못했습니다.");
        var row = rows.get(0);
        member(s, Inputs.number(row, "workspace_id"));
        if (!allowArchived && row.get("archived_at") != null)
            throw new Problem(409, "SCOPE_ARCHIVED", "보관된 사업입니다. 먼저 복원해 주세요.");
        return row;
    }

    public void require(
            SessionSubject s,
            long workspace,
            Collection<String> actions,
            Map<String, Object> state) {
        member(s, workspace);
        var rules = policies.rules();
        Set<String> granted =
                s.administrator()
                        ? Set.of()
                        : new HashSet<>(
                                policies.grants(Inputs.number(state, "active_policy_id"))
                                        .get(tier(workspace)));
        for (String code : actions) {
            if (!PermissionRules.permitted(code, rules, granted, s.administrator()))
                throw new Problem(403, "ACTION_DENIED", "준비 중·비활성·현재 미배정인 기능 또는 의존 기능입니다: " + code);
        }
    }

    public <T> T workspace(
            SessionSubject s, long workspace, Collection<String> actions, Supplier<T> operation) {
        return tx.execute(
                t -> {
                    var state = policies.state(true);
                    require(s, workspace, actions, state);
                    return operation.get();
                });
    }

    public <T> T business(
            SessionSubject s,
            long business,
            boolean archived,
            Collection<String> actions,
            Supplier<T> operation) {
        return tx.execute(
                t -> {
                    var state = policies.state(true);
                    var scope = scope(s, business, archived);
                    require(s, Inputs.number(scope, "workspace_id"), actions, state);
                    return operation.get();
                });
    }

    public Set<String> allowed(SessionSubject s, long workspace) {
        return tx.execute(
                t -> {
                    var state = policies.state(true);
                    member(s, workspace);
                    var rules = policies.rules();
                    var grants =
                            s.administrator()
                                    ? rules.keySet()
                                    : new HashSet<>(
                                            policies.grants(
                                                            Inputs.number(
                                                                    state, "active_policy_id"))
                                                    .get(tier(workspace)));
                    Set<String> out = new LinkedHashSet<>();
                    for (String c : grants) {
                        var r = rules.get(c);
                        if (PermissionRules.permitted(c, rules, grants, s.administrator()))
                            out.add(c);
                    }
                    return out;
                });
    }

    public void event(long workspace, Long business, String type, String object, long version) {
        activity.domain(workspace, business, type, object);
        db.update(
                "INSERT INTO"
                    + " ap_event_outbox(workspace_id,business_scope_id,event_type,object_id,object_version,created_at)"
                    + " VALUES(?,?,?,?,?,?)",
                workspace,
                business,
                type,
                object,
                version,
                LocalDateTime.now(clock));
    }
}
