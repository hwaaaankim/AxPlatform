package com.axplatform.workspace;

import com.axplatform.common.*;
import com.axplatform.policy.*;
import com.axplatform.security.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class DraftService {
    public static final Set<String> WORKFLOWS =
            Set.of("EXCEL", "WORD", "HWP", "PPT", "CRM", "CALENDAR", "APP");
    private static final Set<String> FIELDS =
            Set.of("purpose", "data", "result", "details", "audience");

    public record Create(long businessId, String workflow, String title) {}

    public record Edit(
            String title, Map<String, String> answers, List<String> assetIds, long version) {}

    public record Command(long version, List<String> fields) {}

    private final JdbcTemplate db;
    private final AccessService access;
    private final JsonCodec json;
    private final Clock clock;

    public DraftService(JdbcTemplate db, AccessService access, JsonCodec json, Clock clock) {
        this.db = db;
        this.access = access;
        this.json = json;
        this.clock = clock;
    }

    private Map<String, Object> row(String id) {
        var rows = db.queryForList("SELECT * FROM ap_work_draft WHERE id=?", id);
        if (rows.isEmpty()) throw new Problem(404, "DRAFT_NOT_FOUND", "업무 초안을 찾지 못했습니다.");
        return rows.get(0);
    }

    private Map<String, Object> publicDraft(Map<String, Object> r) {
        var out = new LinkedHashMap<String, Object>();
        for (String key :
                List.of(
                        "id",
                        "workspace_id",
                        "business_scope_id",
                        "workflow",
                        "title",
                        "status",
                        "version",
                        "created_at",
                        "updated_at")) out.put(key, r.get(key));
        out.put("answers", json.object((String) r.get("answers_json")));
        out.put(
                "assetIds",
                db.queryForList(
                        "SELECT asset_id FROM ap_draft_asset WHERE draft_id=? ORDER BY asset_id",
                        String.class,
                        r.get("id")));
        return out;
    }

    public List<Map<String, Object>> list(SessionSubject s, long business, boolean archived) {
        return access.business(
                s,
                business,
                false,
                List.of("workspace.draft.list"),
                () ->
                        db.queryForList(
                                "SELECT id,title,workflow,status,version,updated_at, (SELECT COUNT(*) FROM ap_sheet s WHERE s.draft_id=ap_work_draft.id) AS has_sheet FROM"
                                    + " ap_work_draft WHERE business_scope_id=? AND status"
                                        + (archived ? "='ARCHIVED'" : "<>'ARCHIVED'")
                                        + " ORDER BY updated_at DESC LIMIT 200",
                                business));
    }

    public Map<String, Object> get(SessionSubject s, String id) {
        var r = row(id);
        return access.business(
                s,
                Inputs.number(r, "business_scope_id"),
                false,
                List.of("workspace.draft.read"),
                () -> publicDraft(row(id)));
    }

    public Map<String, Object> create(SessionSubject s, Create i) {
        Inputs.state(i.workflow(), WORKFLOWS);
        String title = Inputs.text(i.title(), 100, "초안명");
        return access.business(
                s,
                i.businessId(),
                false,
                List.of("workspace.draft.create"),
                () -> {
                    var scope = access.scope(s, i.businessId(), false);
                    String id = UUID.randomUUID().toString();
                    var now = LocalDateTime.now(clock);
                    db.update(
                            "INSERT INTO"
                                + " ap_work_draft(id,workspace_id,business_scope_id,creator_account_id,workflow,title,answers_json,saved_title,saved_answers_json,saved_assets_json,status,created_at,updated_at)"
                                + " VALUES(?,?,?,?,?,?,'{}',?,'{}','[]','DRAFT',?,?)",
                            id,
                            scope.get("workspace_id"),
                            i.businessId(),
                            s.accountId(),
                            i.workflow(),
                            title,
                            title,
                            now,
                            now);
                    access.event(
                            Inputs.number(scope, "workspace_id"),
                            i.businessId(),
                            "draft.created",
                            id,
                            0);
                    return Map.of("id", id, "version", 0);
                });
    }

    private void version(Map<String, Object> r, long version) {
        if (Inputs.number(r, "version") != version)
            throw Problem.conflict("다른 화면에서 수정했습니다. 입력을 보존한 뒤 최신 내용을 다시 확인해 주세요.");
    }

    private void assets(SessionSubject s, Map<String, Object> r, List<String> ids) {
        if (ids == null || ids.size() > 20 || new HashSet<>(ids).size() != ids.size())
            throw Problem.invalid("첨부 목록을 확인해 주세요.");
        if (!ids.isEmpty())
            access.require(
                    s,
                    Inputs.number(r, "workspace_id"),
                    List.of("workspace.asset.list"),
                    new HashMap<>(
                            Map.of(
                                    "active_policy_id",
                                    db.queryForObject(
                                            "SELECT active_policy_id FROM ap_policy_state WHERE"
                                                + " id=1",
                                            Long.class))));
        db.queryForList("SELECT id FROM ap_account WHERE id=? FOR UPDATE", s.accountId());
        for (String id : ids)
            if (db.queryForObject(
                            "SELECT COUNT(*) FROM ap_asset WHERE id=? AND owner_account_id=? AND"
                                + " business_scope_id=? AND archived_at IS NULL AND"
                                + " storage_state='READY' AND (scope_kind='COMMON' OR"
                                + " (scope_kind='WORKFLOW' AND workflow=?) OR (scope_kind='PROJECT'"
                                + " AND project_id=?))",
                            Integer.class,
                            id,
                            s.accountId(),
                            r.get("business_scope_id"),
                            r.get("workflow"),
                            r.get("id"))
                    != 1)
                throw new Problem(422, "ASSET_SCOPE", "해당 사업·업무·작업 범위에서 사용 가능한 내 자료만 연결할 수 있습니다.");
        db.update("DELETE FROM ap_draft_asset WHERE draft_id=?", r.get("id"));
        for (String id : ids)
            db.update("INSERT INTO ap_draft_asset(draft_id,asset_id) VALUES(?,?)", r.get("id"), id);
    }

    public Map<String, Object> edit(SessionSubject s, String id, Edit i) {
        var initial = row(id);
        String title = Inputs.text(i.title(), 100, "초안명");
        if (i.answers() == null || !FIELDS.containsAll(i.answers().keySet()))
            throw Problem.invalid("허용된 질문 답변만 저장할 수 있습니다.");
        Map<String, String> answers = new LinkedHashMap<>();
        for (String key : FIELDS) {
            String v = i.answers().getOrDefault(key, "");
            if (v == null || v.length() > 2000 || v.indexOf('\0') >= 0)
                throw Problem.invalid("답변은 항목당 2,000자 이내입니다.");
            answers.put(key, v);
        }
        return access.business(
                s,
                Inputs.number(initial, "business_scope_id"),
                false,
                List.of("workspace.draft.update"),
                () -> {
                    var r = row(id);
                    version(r, i.version());
                    if ("ARCHIVED".equals(r.get("status")))
                        throw Problem.invalid("보관된 초안은 먼저 복원해 주세요.");
                    assets(s, r, i.assetIds() == null ? List.of() : i.assetIds());
                    db.update(
                            "UPDATE ap_work_draft SET"
                                + " title=?,answers_json=?,status='DRAFT',version=version+1,updated_at=?"
                                + " WHERE id=?",
                            title,
                            json.write(answers),
                            LocalDateTime.now(clock),
                            id);
                    access.event(
                            Inputs.number(r, "workspace_id"),
                            Inputs.number(r, "business_scope_id"),
                            "draft.updated",
                            id,
                            i.version() + 1);
                    return publicDraft(row(id));
                });
    }

    public Map<String, Object> command(SessionSubject s, String id, String command, Command i) {
        var initial = row(id);
        String action =
                switch (command) {
                    case "save" -> "workspace.draft.save";
                    case "cancel" -> "workspace.draft.cancel";
                    case "archive" -> "workspace.draft.archive";
                    case "restore" -> "workspace.draft.restore";
                    default -> throw Problem.invalid("변경 작업을 확인해 주세요.");
                };
        return access.business(
                s,
                Inputs.number(initial, "business_scope_id"),
                false,
                List.of(action),
                () -> {
                    var r = row(id);
                    version(r, i.version());
                    if ("ARCHIVED".equals(r.get("status")) && !command.equals("restore"))
                        throw Problem.invalid("보관된 초안은 먼저 복원해 주세요.");
                    var now = LocalDateTime.now(clock);
                    switch (command) {
                        case "save" -> {
                            var ids =
                                    db.queryForList(
                                            "SELECT asset_id FROM ap_draft_asset WHERE draft_id=?"
                                                + " ORDER BY asset_id",
                                            String.class,
                                            id);
                            assets(s, r, ids);
                            db.update(
                                    "UPDATE ap_work_draft SET"
                                        + " status='SAVED',saved_title=title,saved_answers_json=answers_json,saved_assets_json=?,version=version+1,updated_at=?"
                                        + " WHERE id=?",
                                    json.write(ids),
                                    now,
                                    id);
                        }
                        case "cancel" -> {
                            List<String> fields =
                                    i.fields() == null
                                            ? List.of(
                                                    "purpose",
                                                    "data",
                                                    "result",
                                                    "details",
                                                    "audience",
                                                    "title",
                                                    "assets")
                                            : i.fields();
                            if (fields.isEmpty()
                                    || !Set.of(
                                                    "purpose",
                                                    "data",
                                                    "result",
                                                    "details",
                                                    "audience",
                                                    "title",
                                                    "assets")
                                            .containsAll(fields))
                                throw Problem.invalid("취소할 부분을 선택해 주세요.");
                            var current = json.object((String) r.get("answers_json"));
                            var saved = json.object((String) r.get("saved_answers_json"));
                            for (String field : fields)
                                if (FIELDS.contains(field))
                                    current.put(field, saved.getOrDefault(field, ""));
                            String title =
                                    fields.contains("title")
                                            ? (String) r.get("saved_title")
                                            : (String) r.get("title");
                            if (fields.contains("assets")) {
                                Object value = json.value((String) r.get("saved_assets_json"));
                                @SuppressWarnings("unchecked")
                                List<String> ids = (List<String>) value;
                                assets(s, r, ids);
                            }
                            db.update(
                                    "UPDATE ap_work_draft SET"
                                        + " title=?,answers_json=?,status='DRAFT',version=version+1,updated_at=?"
                                        + " WHERE id=?",
                                    title,
                                    json.write(current),
                                    now,
                                    id);
                        }
                        case "archive" ->
                                db.update(
                                        "UPDATE ap_work_draft SET"
                                            + " status='ARCHIVED',version=version+1,updated_at=?"
                                            + " WHERE id=?",
                                        now,
                                        id);
                        case "restore" ->
                                db.update(
                                        "UPDATE ap_work_draft SET"
                                            + " status='DRAFT',version=version+1,updated_at=? WHERE"
                                            + " id=?",
                                        now,
                                        id);
                    }
                    access.event(
                            Inputs.number(r, "workspace_id"),
                            Inputs.number(r, "business_scope_id"),
                            "draft." + command,
                            id,
                            i.version() + 1);
                    return publicDraft(row(id));
                });
    }

    public Map<String, Object> cloneDraft(SessionSubject s, String id, String title) {
        var original = row(id);
        return access.business(
                s,
                Inputs.number(original, "business_scope_id"),
                false,
                List.of("workspace.draft.clone", "workspace.draft.read", "workspace.draft.create"),
                () -> {
                    var source = row(id);
                    var created =
                            create(
                                    s,
                                    new Create(
                                            Inputs.number(source, "business_scope_id"),
                                            (String) source.get("workflow"),
                                            Inputs.text(title, 100, "새 초안명")));
                    String newId = (String) created.get("id");
                    if ("EXCEL".equals(source.get("workflow"))) {
                        var copies =
                                db.queryForList(
                                        "SELECT content_json FROM ap_sheet WHERE draft_id=?", id);
                        if (!copies.isEmpty()) {
                            access.business(
                                    s,
                                    Inputs.number(source, "business_scope_id"),
                                    false,
                                    List.of(
                                            "workspace.sheet.read",
                                            "workspace.sheet.update",
                                            "workspace.sheet.schema.update"),
                                    () -> null);
                            var sheet =
                                    new LinkedHashMap<String, Object>(
                                            com.axplatform.sheet.WorkbookRules.normalize(
                                                    json.object(
                                                            (String)
                                                                    copies.get(0)
                                                                            .get("content_json"))));
                            sheet = new LinkedHashMap<>(com.axplatform.sheet.WorkbookRules.template(sheet));
                            var now = LocalDateTime.now(clock);
                            db.update(
                                    "INSERT INTO"
                                        + " ap_sheet(draft_id,workspace_id,business_scope_id,content_json,previous_json,version,created_at,updated_at)"
                                        + " VALUES(?,?,?,?,NULL,0,?,?)",
                                    newId,
                                    source.get("workspace_id"),
                                    source.get("business_scope_id"),
                                    json.write(sheet),
                                    now,
                                    now);
                        }
                    }
                    db.update(
                            "UPDATE ap_work_draft SET answers_json=?,saved_answers_json=? WHERE"
                                + " id=?",
                            source.get("answers_json"),
                            source.get("answers_json"),
                            newId);
                    var ids =
                            db.queryForList(
                                    "SELECT asset_id FROM ap_draft_asset WHERE draft_id=? ORDER BY"
                                        + " asset_id",
                                    String.class,
                                    id);
                    ids =
                            ids.stream()
                                    .filter(
                                            assetId ->
                                                    db.queryForObject(
                                                                    "SELECT COUNT(*) FROM ap_asset"
                                                                        + " WHERE id=? AND"
                                                                        + " scope_kind<>'PROJECT'"
                                                                        + " AND storage_state='READY'"
                                                                        + " AND archived_at IS"
                                                                        + " NULL",
                                                                    Integer.class,
                                                                    assetId)
                                                            == 1)
                                    .toList();
                    assets(s, row(newId), ids);
                    db.update(
                            "UPDATE ap_work_draft SET saved_assets_json=? WHERE id=?",
                            json.write(ids),
                            newId);
                    return Map.of("id", newId, "version", 0);
                });
    }
}
