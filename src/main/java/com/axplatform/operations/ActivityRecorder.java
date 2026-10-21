package com.axplatform.operations;

import com.axplatform.security.*;

import jakarta.servlet.http.*;

import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

import java.time.*;
import java.util.*;

/**
 * Closed route families, never request bodies, query text, file names, IP addresses or credentials.
 */
@Component
public class ActivityRecorder implements HandlerInterceptor, WebMvcConfigurer {
    private final JdbcTemplate db;
    private final Clock clock;
    private final int retention;
    private final Logger log = LoggerFactory.getLogger(ActivityRecorder.class);

    public ActivityRecorder(JdbcTemplate db, Clock clock, Environment env) {
        this.db = db;
        this.clock = clock;
        retention =
                Math.max(
                        30,
                        env.getProperty(
                                "ax.operations.activity-retention-days", Integer.class, 180));
    }

    public void addInterceptors(InterceptorRegistry r) {
        r.addInterceptor(this);
    }

    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        req.setAttribute("ax.activity.start", System.nanoTime());
        return true;
    }

    public void afterCompletion(
            HttpServletRequest req, HttpServletResponse res, Object handler, Exception error) {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String route)) return;
        String method = req.getMethod(), action = action(method, route);
        if (action == null) return;
        if (!"GET".equals(method)
                && res.getStatus() < 400
                && !route.startsWith("/api/admin/")
                && !route.equals("/api/auth/login")) return;
        SessionSubject s;
        try {
            s = Subjects.current();
        } catch (RuntimeException e) {
            return;
        }
        if (s == null) return;
        String project = null, workflow = null;
        Long business = null;
        try {
            var vars = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (vars instanceof Map<?, ?> m
                    && m.get("id") instanceof String id
                    && route.contains("/drafts/")) {
                var d =
                        db.queryForList(
                                "SELECT d.id,d.workflow,d.business_scope_id FROM ap_work_draft d"
                                    + " JOIN ap_workspace w ON w.id=d.workspace_id WHERE d.id=? AND"
                                    + " w.owner_account_id=?",
                                id,
                                s.accountId());
                if (!d.isEmpty()) {
                    project = id;
                    workflow = (String) d.get(0).get("workflow");
                    business = ((Number) d.get(0).get("business_scope_id")).longValue();
                }
            }
            if (business == null) {
                String b = req.getParameter("businessId");
                if (b != null && b.matches("[0-9]{1,18}")) {
                    var owned =
                            db.queryForList(
                                    "SELECT b.id FROM ap_business_scope b JOIN ap_workspace w ON"
                                        + " w.id=b.workspace_id WHERE b.id=? AND"
                                        + " w.owner_account_id=?",
                                    Long.parseLong(b),
                                    s.accountId());
                    if (!owned.isEmpty()) business = Long.parseLong(b);
                }
            }
            if (workflow == null) {
                String w = req.getParameter("workflow");
                if (com.axplatform.storage.ManagedStorage.WORKFLOWS.contains(
                        Objects.toString(w, ""))) workflow = w;
            }
            long duration =
                    req.getAttribute("ax.activity.start") instanceof Long n
                            ? (System.nanoTime() - n) / 1000000
                            : 0;
            record(
                    s.accountId(),
                    business,
                    project,
                    workflow,
                    action,
                    res.getStatus() < 400 ? "SUCCESS" : "FAILURE",
                    duration,
                    res.getStatus() >= 400 ? "HTTP_" + res.getStatus() : null);
        } catch (RuntimeException e) {
            log.warn("activity persistence failed action={}", action);
        }
    }

    static String action(String method, String route) {
        if ("GET".equals(method)) {
            if (route.equals("/work/new")) return "WORK_START_VIEW";
            if (route.equals("/work/drafts/{id}")) return "WORK_OPEN";
            if (route.equals("/my/files")) return "FILES_VIEW";
            if (route.equals("/dashboard")) return "DASHBOARD_VIEW";
            if (route.endsWith("/download") || route.endsWith(".xlsx")) return "FILE_DOWNLOAD";
            return null;
        }
        if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) return null;
        if (route.startsWith("/api/drafts"))
            return method + ":" + route.replaceAll("\\{[^}]*}", "item");
        if (route.startsWith("/api/assets") || route.startsWith("/api/my/files"))
            return method + ":" + route.replaceAll("\\{[^}]*}", "item");
        if (route.startsWith("/api/sheets")) return "SHEET_IMPORT_PREVIEW";
        if (route.startsWith("/api/ai-runs"))
            return method + ":" + route.replaceAll("\\{[^}]*}", "item");
        if (route.startsWith("/api/admin/"))
            return method + ":" + route.replaceAll("\\{[^}]*}", "item");
        if (route.equals("/api/auth/login")) return "LOGIN";
        return null;
    }

    public void login(Long actor, boolean success) {
        try {
            record(
                    actor,
                    null,
                    null,
                    null,
                    success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE",
                    success ? "SUCCESS" : "FAILURE",
                    0,
                    success ? null : "LOGIN_REJECTED");
        } catch (RuntimeException e) {
            log.warn("login activity persistence failed");
        }
    }

    public void domain(long workspace, Long business, String action, String object) {
        try {
            Long actor = null;
            try {
                actor = Subjects.current().accountId();
            } catch (RuntimeException ignored) {
            }
            String project = null, workflow = null;
            var drafts =
                    db.queryForList("SELECT id,workflow FROM ap_work_draft WHERE id=?", object);
            if (drafts.isEmpty()) {
                var run =
                        db.queryForList(
                                "SELECT r.account_id,d.id,d.workflow FROM ap_ai_run r JOIN"
                                    + " ap_work_draft d ON d.id=r.draft_id WHERE r.id=?",
                                object);
                if (!run.isEmpty()) {
                    drafts = run;
                    actor = ((Number) run.get(0).get("account_id")).longValue();
                }
            }
            if (drafts.isEmpty()) {
                var asset =
                        db.queryForList(
                                "SELECT project_id AS id,workflow,owner_account_id FROM ap_asset"
                                    + " WHERE id=?",
                                object);
                if (!asset.isEmpty()) drafts = asset;
            }
            if (!drafts.isEmpty()) {
                project = (String) drafts.get(0).get("id");
                workflow = (String) drafts.get(0).get("workflow");
            }
            String code =
                    action.equals("ai.failed")
                            ? db.queryForObject(
                                    "SELECT safe_error_code FROM ap_ai_run WHERE id=?",
                                    String.class,
                                    object)
                            : null;
            record(
                    actor,
                    business,
                    project,
                    workflow,
                    action,
                    action.equals("ai.failed") ? "FAILURE" : "SUCCESS",
                    0,
                    code);
        } catch (RuntimeException e) {
            log.warn("domain activity persistence failed action={}", action);
        }
    }

    public void record(
            Long actor,
            Long business,
            String project,
            String workflow,
            String action,
            String outcome,
            long duration,
            String code) {
        log.info(
                "activity actor={} action={} workflow={} outcome={} durationMs={}",
                actor,
                action,
                workflow,
                outcome,
                Math.max(0, duration));
        db.update(
                "INSERT INTO"
                    + " ap_activity_event(actor_account_id,business_scope_id,project_id,workflow,action_code,outcome,duration_ms,safe_error_code,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                actor,
                business,
                project,
                workflow,
                action.length() > 100 ? action.substring(0, 100) : action,
                outcome,
                Math.max(0, duration),
                code,
                LocalDateTime.now(clock));
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
    public void retain() {
        db.update(
                "DELETE FROM ap_activity_event WHERE created_at<?",
                LocalDateTime.now(clock).minusDays(retention));
    }

    public int retention() {
        return retention;
    }
}
