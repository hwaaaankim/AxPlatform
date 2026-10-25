package com.axplatform.sheet;
import com.axplatform.common.Problem;
import java.util.*;
/** A bounded data slice; applying a proposal cannot remove or modify rows outside it. */
public final class AiSheetScope {
 private AiSheetScope(){}
 public static Map<String,Object> range(Map<String,Object> sheet,Integer start,Integer count){
  int rows=((List<?>)sheet.get("rows")).size(),cols=((List<?>)sheet.get("columns")).size();
  if(start==null&&count==null){if(rows>200||(long)rows*cols>4000)throw Problem.invalid("큰 표는 AI 검토 시작 행과 행 수를 선택하세요. 최대 200행·4,000셀입니다.");return Map.of();}
  if(start==null||count==null||start<1||count<1||count>200||(long)count*cols>4000||(long)start+count-1>rows)throw Problem.invalid("AI 검토 범위는 실제 데이터 안에서 최대 200행·4,000셀로 선택하세요.");
  return Map.of("rowStart",start,"rowCount",count);
 }
 @SuppressWarnings("unchecked") public static Map<String,Object> project(Map<String,Object> sheet,Map<String,Object> scope){
  var rows=(List<List<String>>)sheet.get("rows");var ids=(List<String>)sheet.get("rowIds");int start=scope.get("rowStart") instanceof Number n?n.intValue()-1:0,count=scope.get("rowCount") instanceof Number n?n.intValue():rows.size();range(sheet,scope.isEmpty()?null:start+1,scope.isEmpty()?null:count);
  var columns=new ArrayList<Map<String,Object>>();for(var col:(List<Map<String,Object>>)sheet.get("columns")){var c=new LinkedHashMap<String,Object>();for(String k:List.of("id","label","type","unit","width","formula"))if(col.containsKey(k))c.put(k,col.get(k));columns.add(c);}
  var selected=new ArrayList<List<String>>();for(int r=start;r<start+count;r++){var row=new ArrayList<>(rows.get(r));for(int c=0;c<row.size();c++){var f=ExcelCells.<String>map(ExcelCells.meta(sheet),"formulas").get(ExcelCells.ref(r+1,c));if(f!=null)row.set(c,"="+f);}selected.add(row);}if(selected.stream().flatMap(List::stream).mapToLong(String::length).sum()>80000)throw Problem.invalid("선택한 셀의 글자 수가 많습니다. AI 검토 행 수를 줄여 주세요.");return Map.of("columns",columns,"rows",selected,"rowIds",ids.subList(start,start+count));
 }
 @SuppressWarnings("unchecked") public static Map<String,Object> merge(Map<String,Object> original,Map<String,Object> proposal,Map<String,Object> scope){
  if(scope.isEmpty()&&!ExcelCells.hasAny(original)){range(original,null,null);return proposal;}
  if(scope.isEmpty()){
   range(original,null,null);var normalized=SheetRules.normalize(proposal);var oldColumns=(List<Map<String,Object>>)original.get("columns");var newColumns=(List<Map<String,Object>>)normalized.get("columns");var oldIds=(List<String>)original.get("rowIds");var newIds=(List<String>)normalized.get("rowIds");
   if(newColumns.size()<oldColumns.size()||newIds.size()<oldIds.size()||!newIds.subList(0,oldIds.size()).equals(oldIds))throw Problem.invalid("원본 수식이 있는 표의 행 삭제·재정렬은 직접 편집에서 실행하세요. AI는 기존 순서를 유지하고 끝에 추가할 수 있습니다.");
   for(int c=0;c<oldColumns.size();c++)if(!oldColumns.get(c).get("id").equals(newColumns.get(c).get("id")))throw Problem.invalid("원본 수식이 있는 표의 열 이동·삭제는 직접 편집에서 실행하세요.");
   var result=ExcelCells.mutable(original);var cols=new ArrayList<Map<String,Object>>();for(int c=0;c<newColumns.size();c++){var col=new LinkedHashMap<String,Object>();if(c<oldColumns.size())col.putAll(oldColumns.get(c));col.putAll(newColumns.get(c));cols.add(col);}result.put("columns",cols);result.put("rows",normalized.get("rows"));result.put("rowIds",newIds);var meta=ExcelCells.meta(result);meta.put("formulas",new LinkedHashMap<>(ExcelCells.map(ExcelCells.meta(normalized),"formulas")));meta.put("issues",Map.of());
   // Preserve formatting when dimensions are unchanged; appending needs a freshly sized style grid.
   if((newColumns.size()!=oldColumns.size()||newIds.size()!=oldIds.size())&&original.get("presentation") instanceof Map<?,?> p){var presentation=new LinkedHashMap<String,Object>((Map<String,Object>)p);if(p.get("cellStyles") instanceof List<?> styleRows){var expanded=new ArrayList<List<Integer>>();for(int r=0;r<=newIds.size();r++){var row=new ArrayList<Integer>(Collections.nCopies(newColumns.size(),0));if(r<styleRows.size()){var oldStyle=(List<Integer>)styleRows.get(r);for(int c=0;c<oldStyle.size();c++)row.set(c,oldStyle.get(c));}expanded.add(row);}presentation.put("cellStyles",expanded);}result.put("presentation",presentation);}
   for(var e:new ArrayList<>(ExcelCells.<Map<String,Object>>map(meta,"paused").entrySet())){if(ExcelCells.map(meta,"formulas").containsKey(e.getKey()))ExcelCells.map(meta,"paused").remove(e.getKey());else if(!ExcelCells.value(original,e.getKey()).equals(ExcelCells.value(result,e.getKey()))){var backup=new LinkedHashMap<>(e.getValue());backup.put("mode","USER_INPUT");ExcelCells.map(meta,"paused").put(e.getKey(),backup);}}
   for(String ref:new ArrayList<>(ExcelCells.map(meta,"blocked").keySet()))if(!Objects.equals(ExcelCells.map(meta,"formulas").get(ref),ExcelCells.map(ExcelCells.meta(original),"formulas").get(ref)))ExcelCells.map(meta,"blocked").remove(ref);
   return SheetRules.normalize(result);
  }
  var projected=project(original,scope);var normalized=SheetRules.normalize(proposal);
  if(!projected.get("columns").equals(normalized.get("columns"))||!projected.get("rowIds").equals(normalized.get("rowIds"))||((List<?>)projected.get("rows")).size()!=((List<?>)normalized.get("rows")).size())throw Problem.invalid("범위 편집은 선택한 행의 셀 값만 바꿀 수 있습니다. 열 구성·행 순서를 유지해 다시 요청하세요.");
  var result=ExcelCells.mutable(original);var rows=(List<List<String>>)result.get("rows");int at=((Number)scope.get("rowStart")).intValue()-1;var fm=ExcelCells.<String>map(ExcelCells.meta(result),"formulas");var proposed=ExcelCells.<String>map(ExcelCells.meta(normalized),"formulas");int local=0;
  for(var row:(List<List<String>>)normalized.get("rows")){for(int c=0;c<row.size();c++){String target=ExcelCells.ref(at+1,c),source=ExcelCells.ref(local+1,c);if(proposed.containsKey(source))fm.put(target,proposed.get(source));else fm.remove(target);if(!Objects.equals(fm.get(target),ExcelCells.map(ExcelCells.meta(original),"formulas").get(target)))ExcelCells.map(ExcelCells.meta(result),"blocked").remove(target);ExcelCells.map(ExcelCells.meta(result),"issues").remove(target);ExcelCells.map(ExcelCells.meta(result),"paused").remove(target);}rows.set(at++,new ArrayList<>(row));local++;}return SheetRules.normalize(result);
 }
 public static Map<String,Object> fromRequest(Map<String,Object> request){return request.get("rowStart") instanceof Number start&&request.get("rowCount") instanceof Number count?Map.of("rowStart",start.intValue(),"rowCount",count.intValue()):Map.of();}
}
