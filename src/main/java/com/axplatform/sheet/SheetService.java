package com.axplatform.sheet;

import com.axplatform.common.*;
import com.axplatform.policy.AccessService;
import com.axplatform.security.SessionSubject;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SheetService {
    public record Export(String filename, byte[] data, long version) { }
    private final JdbcTemplate db;
    private final AccessService access;
    private final JsonCodec json;
    private final Clock clock;

    public SheetService(JdbcTemplate db, AccessService access, JsonCodec json, Clock clock) {
        this.db = db; this.access = access; this.json = json; this.clock = clock;
    }
    private Map<String,Object> draft(String id) {
        var found = db.queryForList("SELECT id,workspace_id,business_scope_id,workflow,title,status,version FROM ap_work_draft WHERE id=?", id);
        if (found.isEmpty()) throw new Problem(404, "DRAFT_NOT_FOUND", "업무 초안을 찾지 못했습니다.");
        var row = found.get(0);
        if (!"EXCEL".equals(row.get("workflow"))) throw Problem.invalid("기본 표는 엑셀 업무에서 사용할 수 있습니다.");
        return row;
    }
    private Map<String,Object> sheetRow(String id) {
        var rows = db.queryForList("SELECT * FROM ap_sheet WHERE draft_id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private Map<String,Object> content(Map<String,Object> row) {
        return row == null ? WorkbookRules.empty() : WorkbookRules.normalize(json.object((String) row.get("content_json")));
    }
    private Map<String,Object> view(Map<String,Object> d, Map<String,Object> row) {
        var result = new LinkedHashMap<String,Object>();
        result.put("draftId", d.get("id")); result.put("draftVersion", d.get("version"));
        result.put("version", row == null ? -1L : Inputs.number(row, "version"));
        var book=content(row); result.put("workbook",book); result.put("sheet", WorkbookRules.data(book,null)); result.put("canUndo", row != null && row.get("previous_json") != null);
        return result;
    }
    public Map<String,Object> get(SessionSubject subject, String id) {
        var first = draft(id);
        return access.business(subject, Inputs.number(first, "business_scope_id"), false,
            List.of("workspace.sheet.read", "workspace.draft.read"), () -> view(draft(id), sheetRow(id)));
    }
    public Map<String,Object> put(SessionSubject subject, String id, Map<String,Object> input, long version, long draftVersion) {
        return save(subject, id, SheetRules.normalize(input), version, draftVersion, true, false, false, null);
    }
    /** Called after DraftService.edit inside the AI apply transaction; a failed diff rolls back both. */
    public Map<String,Object> applyAi(SessionSubject subject, String id, Map<String,Object> input, long version, long draftVersion) {
        return applyAi(subject,id,input,version,draftVersion,null);
    }
    public Map<String,Object> undo(SessionSubject subject, String id, long version, long draftVersion) {
        return save(subject, id, null, version, draftVersion, true, true, false, null);
    }
    private Map<String,Object> save(SessionSubject subject, String id, Map<String,Object> proposal,
                                    long expectedVersion, long expectedDraft, boolean bumpDraft, boolean undo, boolean ai, String targetSheetId) {
        var first = draft(id);
        List<String> baseActions = new ArrayList<>(List.of("workspace.sheet.update", "workspace.sheet.read", "workspace.draft.update", "workspace.draft.read"));
        if (ai) baseActions.add("workspace.ai.apply");
        return access.business(subject, Inputs.number(first, "business_scope_id"), false, baseActions, () -> {
            var d = draft(id); var old = sheetRow(id);
            if ("ARCHIVED".equals(d.get("status"))) throw Problem.invalid("보관된 초안은 먼저 복원해 주세요.");
            long oldVersion = old == null ? -1 : Inputs.number(old, "version");
            if (oldVersion != expectedVersion || Inputs.number(d, "version") != expectedDraft)
                throw Problem.conflict("다른 화면에서 초안 또는 표가 바뀌었습니다. 현재 입력은 보존하고 최신 버전과 비교해 주세요.");
            Map<String,Object> before = content(old), after = proposal;
            if (undo) {
                if (old == null || old.get("previous_json") == null) throw Problem.invalid("되돌릴 이전 표가 없습니다.");
                after = WorkbookRules.normalize(json.object((String) old.get("previous_json")));
            }
            if(!undo && !proposal.containsKey("sheets")) {
                if(targetSheetId==null && WorkbookRules.sheets(before).size()>1) throw Problem.invalid("여러 시트 중 적용할 시트를 지정하세요.");
                after=WorkbookRules.replace(before,targetSheetId,proposal);
            }
            var required = WorkbookRules.requiredActions(before, after);
            Set<Object> priorSources=new HashSet<>();for(var entry:WorkbookRules.sheets(before))priorSources.addAll((List<?>)entry.get("sourceAssetIds"));
            for(var entry:WorkbookRules.sheets(after)) for(Object asset:(List<?>)entry.get("sourceAssetIds")) {
                if(undo||priorSources.contains(asset))continue;
                if(db.queryForObject("SELECT COUNT(*) FROM ap_draft_asset WHERE draft_id=? AND asset_id=?",Integer.class,id,asset)==0)
                    throw Problem.invalid("시트 ‘"+entry.get("name")+"’의 원본 자료를 먼저 이 작업에 연결하세요.");
            }
            if (before.equals(after) && !undo) return view(d, old);
            // Do not trust an editable catalog dependency as the sole protection of actual mutations.
            for (String action : required)
                access.business(subject, Inputs.number(d, "business_scope_id"), false, List.of(action), () -> null);
            after=WorkbookCalculation.calculate(after);
            LocalDateTime now = LocalDateTime.now(clock);
            String saved = json.write(after), previous = undo ? null : json.write(before);
            if(saved.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>14_000_000 && db.queryForObject("SELECT COUNT(*) FROM ap_schema_migration WHERE version='V006'",Integer.class)==0)throw Problem.invalid("수식 포함 데이터 저장 용량 확장이 필요합니다. 13번 SQL(V006)을 적용한 뒤 다시 저장하세요. 현재 편집 내용은 유지됩니다.");
            if(saved.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>14_000_000){
                boolean maria=Boolean.TRUE.equals(db.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)connection->{String product=connection.getMetaData().getDatabaseProductName();return product.contains("MariaDB")||product.contains("MySQL");}));
                if(maria&&db.queryForObject("SELECT @@session.max_allowed_packet",Long.class)<saved.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1024L)throw Problem.invalid("DB 패킷 한도가 표 크기보다 작습니다. 13번 SQL 안내대로 max_allowed_packet을 64MB 이상으로 변경하고 앱을 재시작하세요. 편집 내용은 현재 화면에 유지됩니다.");
            }
            if (old == null) {
                db.update("INSERT INTO ap_sheet(draft_id,workspace_id,business_scope_id,content_json,previous_json,version,created_at,updated_at) VALUES(?,?,?,?,?,0,?,?)",
                    id, d.get("workspace_id"), d.get("business_scope_id"), saved, previous, now, now);
            } else {
                int changed = db.update("UPDATE ap_sheet SET previous_json="+(undo?"NULL":"content_json")+",content_json=?,version=version+1,updated_at=? WHERE draft_id=? AND version=?",
                    saved, now, id, expectedVersion);
                if (changed != 1) throw Problem.conflict("표가 먼저 변경되었습니다.");
            }
            if (bumpDraft) {
                int changed = db.update("UPDATE ap_work_draft SET version=version+1,status='DRAFT',updated_at=? WHERE id=? AND version=?", now, id, expectedDraft);
                if (changed != 1) throw Problem.conflict("초안이 먼저 변경되었습니다.");
            }
            long nextDraft = expectedDraft + (bumpDraft ? 1 : 0);
            access.event(Inputs.number(d, "workspace_id"), Inputs.number(d, "business_scope_id"), undo ? "sheet.undone" : "sheet.updated", id, nextDraft);
            return view(draft(id), sheetRow(id));
        });
    }
    public Map<String,Object> putWorkbook(SessionSubject subject,String id,Map<String,Object> book,long version,long draftVersion) {
        return save(subject,id,WorkbookRules.normalize(book),version,draftVersion,true,false,false,null);
    }
    public Map<String,Object> applyAi(SessionSubject subject,String id,Map<String,Object> input,long version,long draftVersion,String sheetId) {
        return applyAi(subject,id,input,version,draftVersion,sheetId,Map.of());
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> applyAi(SessionSubject subject,String id,Map<String,Object> input,long version,long draftVersion,String sheetId,Map<String,Object> scope) {
        var current=(Map<String,Object>)getSheet(subject,id,sheetId).get("sheet");
        return save(subject,id,AiSheetScope.merge(current,SheetRules.normalize(input),scope),version,draftVersion,false,false,true,sheetId);
    }
    public Map<String,Object> applyAiWorkbook(SessionSubject subject,String id,Map<String,Object> book,long version,long draftVersion) {
        return save(subject,id,WorkbookRules.normalize(book),version,draftVersion,false,false,true,null);
    }
    public Map<String,Object> getSheet(SessionSubject subject,String id,String sheetId) {
        var result=new LinkedHashMap<>(get(subject,id));
        @SuppressWarnings("unchecked") var book=(Map<String,Object>)result.get("workbook");
        var target=WorkbookRules.sheet(book,sheetId);result.put("sheet",target.get("data"));result.put("sheetId",target.get("id"));result.put("sheetName",target.get("name"));return result;
    }
    public void checkExport(SessionSubject subject, String id, boolean template) {
        var first = draft(id);
        access.business(subject, Inputs.number(first, "business_scope_id"), false,
            List.of("workspace.sheet.read", "workspace.draft.read", template ? "workspace.sheet.export.template" : "workspace.sheet.export"), () -> null);
    }
    public Export export(SessionSubject subject, String id, boolean template) {
        var first = draft(id);
        Map<String,Object> snapshot = access.business(subject, Inputs.number(first, "business_scope_id"), false,
            List.of("workspace.sheet.read", "workspace.draft.read", template ? "workspace.sheet.export.template" : "workspace.sheet.export"), () -> {
                var result = new LinkedHashMap<String,Object>(view(draft(id), sheetRow(id)));
                result.put("title", draft(id).get("title")); return result;
            });
        @SuppressWarnings("unchecked") Map<String,Object> values = new LinkedHashMap<>((Map<String,Object>) snapshot.get("workbook"));
        if (template) values = WorkbookRules.template(values);
        String title = ((String) snapshot.get("title")).replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
        if (title.isBlank()) title = "AxPlatform";
        try {
            return new Export(title + (template ? "_빈양식" : "_데이터") + ".xlsx", XlsxWriter.workbookBytes(values), Inputs.number(snapshot, "version"));
        } catch (IOException e) { throw new Problem(503, "EXPORT_FAILED", "엑셀 파일을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."); }
    }
}
