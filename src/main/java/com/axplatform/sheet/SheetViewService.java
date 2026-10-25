package com.axplatform.sheet;

import com.axplatform.common.*;
import com.axplatform.policy.AccessService;
import com.axplatform.security.SessionSubject;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SheetViewService {
    private final JdbcTemplate db;private final AccessService access;private final SheetService sheets;private final JsonCodec json;private final Clock clock;
    public SheetViewService(JdbcTemplate db,AccessService access,SheetService sheets,JsonCodec json,Clock clock){this.db=db;this.access=access;this.sheets=sheets;this.json=json;this.clock=clock;}
    private long business(String id){var rows=db.queryForList("SELECT business_scope_id FROM ap_work_draft WHERE id=? AND workflow='EXCEL'",id);if(rows.isEmpty())throw new Problem(404,"SHEET_NOT_FOUND","표 업무가 없습니다.");return Inputs.number(rows.get(0),"business_scope_id");}
    @SuppressWarnings("unchecked")
    private Map<String,Object> analyzeSnapshot(SessionSubject actor,String id,Map<String,Object> config){var snapshot=sheets.getSheet(actor,id,Objects.toString(config.get("sheetId"),WorkbookRules.LEGACY_ID));return config.containsKey("format")?DataReport.page(DataReport.build((Map<String,Object>)snapshot.get("sheet"),config),0,Inputs.number(snapshot,"version")):SheetAnalysis.calculate((Map<String,Object>)snapshot.get("sheet"),config,Inputs.number(snapshot,"version"));}
    public Object analyze(SessionSubject actor,String id,Map<String,Object> config){return access.business(actor,business(id),false,List.of("workspace.sheet.analysis.read"),()->analyzeSnapshot(actor,id,config));}
    public Object list(SessionSubject actor,String id){return access.business(actor,business(id),false,List.of("workspace.sheet.analysis.read","workspace.sheet.read"),()->{
        sheets.get(actor,id);List<Map<String,Object>> results=new ArrayList<>();for(var row:db.queryForList("SELECT id,name,config_json,version,updated_at FROM ap_sheet_view WHERE draft_id=? ORDER BY updated_at DESC",id)){var out=new LinkedHashMap<>(row);out.put("config",json.object((String)out.remove("config_json")));results.add(out);}return results;});}
    public Object save(SessionSubject actor,String id,String viewId,long expected,long sheetVersion,Map<String,Object> config){return access.business(actor,business(id),false,List.of("workspace.sheet.analysis.manage","workspace.sheet.analysis.read"),()->{
        var d=db.queryForMap("SELECT status FROM ap_work_draft WHERE id=?",id);if("ARCHIVED".equals(d.get("status")))throw Problem.invalid("보관된 업무는 먼저 복원하세요.");
        var result=analyzeSnapshot(actor,id,config);if(Inputs.number(result,"sheetVersion")!=sheetVersion)throw Problem.conflict("표가 바뀌었습니다. 집계를 다시 확인한 뒤 보기를 저장하세요.");var clean=config.containsKey("format")?DataReport.config(config):SheetAnalysis.config(config);LocalDateTime now=LocalDateTime.now(clock);String target=viewId;
        if(expected>=0&&(target==null||target.isBlank()))throw Problem.invalid("수정할 보기를 선택하세요.");
        if(expected<0){
            if(target==null||target.isBlank())target=UUID.randomUUID().toString();
            if(!target.matches("[a-fA-F0-9-]{36}"))throw Problem.invalid("분석 보기 요청 ID를 확인하세요.");
            var existing=db.queryForList("SELECT draft_id,config_json FROM ap_sheet_view WHERE id=?",target);
            if(!existing.isEmpty()){
                if(!id.equals(existing.get(0).get("draft_id"))||!json.object((String)existing.get(0).get("config_json")).equals(clean))throw Problem.conflict("같은 보기 생성 요청의 내용이 달라졌습니다. 새 보기를 선택하세요.");
                return Map.of("id",target,"status","saved");
            }
            if(db.queryForObject("SELECT COUNT(*) FROM ap_sheet_view WHERE draft_id=?",Integer.class,id)>=20)throw Problem.invalid("저장한 보기는 표당 최대 20개입니다.");
            db.update("INSERT INTO ap_sheet_view(id,draft_id,name,config_json,version,created_at,updated_at) VALUES(?,?,?,?,0,?,?)",target,id,clean.get("name"),json.write(clean),now,now);
        }
        else if(db.update("UPDATE ap_sheet_view SET name=?,config_json=?,version=version+1,updated_at=? WHERE id=? AND draft_id=? AND version=?",clean.get("name"),json.write(clean),now,target,id,expected)!=1)throw Problem.conflict("저장한 보기가 변경되었거나 없습니다. 다시 불러오세요.");
        return Map.of("id",target,"status","saved");});}
    public Object delete(SessionSubject actor,String id,String viewId,long expected){return access.business(actor,business(id),false,List.of("workspace.sheet.analysis.manage","workspace.sheet.analysis.read"),()->{sheets.get(actor,id);if(db.update("DELETE FROM ap_sheet_view WHERE id=? AND draft_id=? AND version=?",viewId,id,expected)!=1)throw Problem.conflict("보기가 변경되었거나 없습니다.");return Map.of("status","deleted");});}
}
