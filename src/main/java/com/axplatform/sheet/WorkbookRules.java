package com.axplatform.sheet;

import com.axplatform.common.JsonCodec;
import com.axplatform.common.Problem;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A project owns one versioned workbook. Legacy single-table JSON is read without migration. */
public final class WorkbookRules {
    public static final int MAX_SHEETS = 20;
    public static final long MAX_WORKBOOK_CELLS=2_000_000;
    public static final String LEGACY_ID = "sheet1";
    private WorkbookRules() {}

    public static Map<String,Object> empty() { return normalize(SheetRules.empty()); }

    public static String name(Object value) {
        if (!(value instanceof String text)) throw Problem.invalid("시트 이름을 입력하세요.");
        text=text.strip();
        if (text.isEmpty() || text.length()>31 || text.matches("(?s).*[\\\\/?*:\\[\\]\\p{Cntrl}].*")
                || text.startsWith("'") || text.endsWith("'"))
            throw Problem.invalid("시트 이름은 1~31자이며 \\ / ? * : [ ] 및 앞뒤 작은따옴표를 사용할 수 없습니다.");
        return text;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> normalize(Map<String,Object> raw) {
        if(raw==null) throw Problem.invalid("통합문서를 확인하세요.");
        if(!raw.containsKey("sheets")) return Map.of("format","AX_WORKBOOK_1","activeSheetId",LEGACY_ID,
                "sheets",List.of(Map.of("id",LEGACY_ID,"name","시트1","data",SheetRules.normalize(raw),"sourceAssetIds",List.of())));
        if(!Set.of("format","sheets","activeSheetId").containsAll(raw.keySet())
                || !"AX_WORKBOOK_1".equals(raw.get("format")) || !(raw.get("sheets") instanceof List<?> entries)
                || entries.isEmpty() || entries.size()>MAX_SHEETS) throw Problem.invalid("프로젝트에는 1~20개의 시트를 저장할 수 있습니다.");
        long totalCells=0;List<Map<String,Object>> sheets=new ArrayList<>();Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Object entry:entries) {
            if(!(entry instanceof Map<?,?> s) || !Set.of("id","name","data","sourceAssetIds").containsAll(s.keySet())
                    || !(s.get("id") instanceof String id) || !id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                    || !ids.add(id) || !(s.get("data") instanceof Map<?,?> data)) throw Problem.invalid("시트 ID·데이터가 없거나 중복되었습니다.");
            if(data.get("rows") instanceof List<?> rows && data.get("columns") instanceof List<?> cols){totalCells+=(long)rows.size()*cols.size();if(totalCells>MAX_WORKBOOK_CELLS)throw Problem.invalid("프로젝트 전체 데이터는 200만 셀 이내로 나누세요.");}
            String title=name(s.get("name"));
            if(!names.add(title.toLowerCase(Locale.ROOT))) throw Problem.invalid("시트 이름 ‘"+title+"’이 중복됩니다. 다른 이름을 입력하세요.");
            Object source=s.containsKey("sourceAssetIds")?s.get("sourceAssetIds"):List.of();
            if(!(source instanceof List<?> assets) || assets.size()>20 || new HashSet<>(assets).size()!=assets.size()
                    || assets.stream().anyMatch(v->!(v instanceof String a)||!a.matches("[a-fA-F0-9-]{36}"))) throw Problem.invalid(title+": 연결 자료 ID를 확인하세요.");
            try { sheets.add(Map.of("id",id,"name",title,"data",SheetRules.normalize((Map<String,Object>)data),"sourceAssetIds",List.copyOf(assets))); }
            catch(Problem error) { throw new Problem(error.status(),error.code(),"시트 ‘"+title+"’: "+error.getMessage()); }
        }
        String active=Objects.toString(raw.get("activeSheetId"),"");
        if(!ids.contains(active)) throw Problem.invalid("선택한 시트가 없습니다. 시트를 다시 선택하세요.");
        var book=Map.<String,Object>of("format","AX_WORKBOOK_1","sheets",List.copyOf(sheets),"activeSheetId",active);
        if(new JsonCodec().write(book).getBytes(StandardCharsets.UTF_8).length>32_000_000) throw Problem.invalid("프로젝트의 전체 시트 데이터는 UTF-8 기준 32MB 이하로 나누세요.");
        return book;
    }

    @SuppressWarnings("unchecked") public static List<Map<String,Object>> sheets(Map<String,Object> book) { return (List<Map<String,Object>>)book.get("sheets"); }
    public static Map<String,Object> sheet(Map<String,Object> book,String id) {
        String target=id==null||id.isBlank()?(String)book.get("activeSheetId"):id;
        return sheets(book).stream().filter(s->target.equals(s.get("id"))).findFirst()
                .orElseThrow(()->new Problem(409,"SHEET_REMOVED","선택한 시트가 삭제되었거나 없습니다. 다른 시트를 선택하세요."));
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> data(Map<String,Object> book,String id) { return (Map<String,Object>)sheet(book,id).get("data"); }
    public static Map<String,Object> replace(Map<String,Object> book,String id,Map<String,Object> data) {
        var target=sheet(book,id);List<Map<String,Object>> values=new ArrayList<>();
        for(var s:sheets(book)){var next=new LinkedHashMap<>(s);if(s.get("id").equals(target.get("id")))next.put("data",data);values.add(next);}
        return normalize(Map.of("format","AX_WORKBOOK_1","activeSheetId",book.get("activeSheetId"),"sheets",values));
    }
    public static Map<String,Object> template(Map<String,Object> book) {
        var result=book;
        for(var s:sheets(book)){var data=new LinkedHashMap<>(data(book,(String)s.get("id")));data.put("rows",List.of());data.put("rowIds",List.of());data.remove("presentation");data.remove("view");data.remove("excel");result=replace(result,(String)s.get("id"),data);}
        List<Map<String,Object>> cleared=new ArrayList<>();for(var s:sheets(result)){var n=new LinkedHashMap<>(s);n.put("sourceAssetIds",List.of());cleared.add(n);}
        return Map.of("format","AX_WORKBOOK_1","activeSheetId",result.get("activeSheetId"),"sheets",cleared);
    }
    public static Set<String> requiredActions(Map<String,Object> before,Map<String,Object> after) {
        Set<String> actions=new LinkedHashSet<>();
        var oldIds=sheets(before).stream().map(s->s.get("id")).toList();var newIds=sheets(after).stream().map(s->s.get("id")).toList();
        if(!oldIds.equals(newIds))actions.add("workspace.sheet.schema.update");
        if(!newIds.containsAll(oldIds))actions.add("workspace.sheet.rows.delete");
        for(var s:sheets(after)){
            String id=(String)s.get("id");var old=sheets(before).stream().filter(p->id.equals(p.get("id"))).findFirst();
            if(old.isPresent() && (!s.get("name").equals(old.get().get("name")) || !s.get("sourceAssetIds").equals(old.get().get("sourceAssetIds")))) actions.add("workspace.sheet.schema.update");
            actions.addAll(SheetRules.requiredActions(old.isPresent()?data(before,id):SheetRules.empty(),data(after,id)));
        }
        return actions;
    }
}
