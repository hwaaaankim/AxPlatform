package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reviewed, bounded commands executed against the complete server workbook, never an AI sample. */
public final class AiSheetOperations {
 private AiSheetOperations(){}
 public static final List<String> KINDS=List.of("SET_CELL","CLEAR_RANGE","CLEAR_ERRORS","PAUSE_ERRORS","ADD_COLUMN","ADD_CALCULATED_COLUMN","DELETE_ROWS","APPEND_ROWS");
 public static final List<String> FIELDS=List.of("kind","range","value","label","columnId","type","formula");
 public record Result(Map<String,Object> workbook,List<Map<String,Object>> preview){}
 public static List<Map<String,Object>> normalize(Object raw){
  if(!(raw instanceof List<?> values)||values.size()>20)throw Problem.invalid("AI 변경 작업은 최대 20개입니다.");
  var result=new ArrayList<Map<String,Object>>();int appendedRows=0;long appendedCells=0,appendedChars=0;
  for(Object item:values){
   if(!(item instanceof Map<?,?> map))throw Problem.invalid("AI 변경 작업의 항목을 확인하세요.");var expected=new HashSet<>(FIELDS);if(map.containsKey("rows"))expected.add("rows");if(!map.keySet().equals(expected))throw Problem.invalid("AI 변경 작업의 항목을 확인하세요.");
   var op=new LinkedHashMap<String,Object>();for(String key:FIELDS){Object v=map.get(key);int max=key.equals("formula")?4000:key.equals("value")?2000:key.equals("label")?100:100;if(!(v instanceof String s)||s.length()>max||s.chars().anyMatch(c->c==0||c<32&&c!=9&&c!=10&&c!=13))throw Problem.invalid("AI 변경 작업의 "+key+" 형식·길이를 확인하세요.");op.put(key,v);}
   String kind=str(op,"kind");if(!KINDS.contains(kind))throw Problem.invalid("지원하지 않는 AI 변경 작업입니다.");
   if(!Set.of("","TEXT","NUMBER","DATE").contains(str(op,"type")))throw Problem.invalid("추가할 열 형식을 확인하세요.");
   if(kind.startsWith("ADD_")&&str(op,"label").isBlank())throw Problem.invalid("추가할 열 이름을 입력하세요.");
   if(kind.equals("ADD_COLUMN")&&!Set.of("TEXT","NUMBER","DATE").contains(str(op,"type")))throw Problem.invalid("추가할 열 형식을 입력하세요.");
   if(kind.equals("ADD_COLUMN")&&!str(op,"formula").isBlank())throw Problem.invalid("계산 열은 ADD_CALCULATED_COLUMN으로 요청하세요.");
   if(kind.equals("ADD_CALCULATED_COLUMN")&&str(op,"formula").isBlank())throw Problem.invalid("계산 열 수식을 입력하세요.");
   if(Set.of("CLEAR_ERRORS","PAUSE_ERRORS").contains(kind)&&!Set.of("","#REF!","#DIV/0!","#VALUE!","#NAME?","#NUM!","#N/A","#NULL!","#CIRC!","ALL").contains(str(op,"formula")))throw Problem.invalid("제거할 오류 종류를 확인하세요.");
   if(map.containsKey("rows")){
    if(!(map.get("rows") instanceof List<?> supplied)||supplied.size()>200)throw Problem.invalid("AI 행 추가는 한 작업당 새 데이터 200행 이하입니다.");
    var newRows=new ArrayList<List<String>>();long cells=0,chars=0;for(Object row:supplied){if(!(row instanceof List<?> valuesRow)||valuesRow.size()>100)throw Problem.invalid("추가할 행은 최대100개 문자열 셀 배열입니다.");var strings=new ArrayList<String>();for(Object cell:valuesRow){if(!(cell instanceof String value)||value.length()>SheetRules.MAX_CELL_LENGTH)throw Problem.invalid("추가할 셀은 최대2000자 문자열입니다.");strings.add(value);chars+=value.length();}cells+=strings.size();newRows.add(List.copyOf(strings));}
    if(cells>4000||chars>80000)throw Problem.invalid("AI 행 추가는 한 작업당 새 데이터4000셀·80000자 이하입니다.");
    if(!kind.equals("APPEND_ROWS")&&!newRows.isEmpty())throw Problem.invalid("행 데이터는 APPEND_ROWS 작업에만 지정하세요.");appendedRows+=newRows.size();appendedCells+=cells;appendedChars+=chars;if(appendedRows>200||appendedCells>4000||appendedChars>80000)throw Problem.invalid("한 번의 AI 제안에 추가할 새 데이터는 합계200행·4000셀·80000자 이하입니다. 나누어 요청하세요.");op.put("rows",List.copyOf(newRows));
   }
   if(kind.equals("APPEND_ROWS")&&(!(op.get("rows") instanceof List<?> incoming)||incoming.isEmpty()))throw Problem.invalid("추가할 새 데이터 행을 입력하세요.");
   result.add(op);
  }return List.copyOf(result);
 }
 public static Map<String,Object> sample(Map<String,Object> data){
  int rows=DataReport.rows(data).size(),cols=DataReport.columns(data).size();int count=Math.min(rows,Math.min(20,Math.max(1,4000/Math.max(1,cols))));
  Map<String,Object> selected=SheetRules.empty();
  if(rows==0)selected=AiSheetScope.project(data,Map.of());else{while(count>0){try{selected=AiSheetScope.project(data,Map.of("rowStart",1,"rowCount",count));break;}catch(Problem tooMuch){if(count==1)throw tooMuch;count=Math.max(1,count/2);}}}
  var result=new LinkedHashMap<String,Object>();result.put("sample",selected);result.put("totalRows",rows);result.put("totalColumns",cols);result.put("sampleDataRowStart",1);result.put("sampleRows",count);result.put("sampleOnly",true);result.put("excelHeaderRow",1);result.put("operationsOnly",true);
  var errors=new LinkedHashMap<String,Integer>();var samples=new ArrayList<Map<String,Object>>();for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){String address=ExcelCells.ref(r+1,c),value=DataReport.rows(data).get(r).get(c);String f=ExcelCells.<String>map(ExcelCells.meta(data),"formulas").getOrDefault(address,"");if(ExcelCells.error(value)||brokenRef(data,address,f)){String code=brokenRef(data,address,f)?"#REF!":value;errors.merge(code,1,Integer::sum);if(samples.size()<12)samples.add(Map.of("address",address,"value",value,"formula",f));}}
  result.put("errorCounts",errors);result.put("errorSamples",samples);
  var calculated=new ArrayList<Map<String,Object>>();for(int c=0;c<cols;c++){var column=DataReport.columns(data).get(c);String ref=ExcelCells.ref(rows,c);if(column.containsKey("formula")||rows>0&&ExcelCells.hasFormula(data,ref))calculated.add(Map.of("columnId",column.get("id"),"label",column.get("label"),"columnIndex",c+1));}result.put("appendFormulaColumns",calculated);return result;
 }
 @SuppressWarnings("unchecked") public static Result apply(Map<String,Object> original,String sheetId,Object requested){
  var operations=normalize(requested);var book=WorkbookRules.normalize(original);WorkbookRules.sheet(book,sheetId);var previews=new ArrayList<Map<String,Object>>();
  for(int index=0;index<operations.size();index++){
   var op=operations.get(index);String kind=str(op,"kind");var old=WorkbookRules.data(book,sheetId);var data=ExcelCells.mutable(old);var rows=DataReport.rows(data);var columns=DataReport.columns(data);long cells=0;var touched=new HashSet<Integer>();var samples=new ArrayList<Map<String,Object>>();
   if(kind.equals("APPEND_ROWS")){
    var incoming=(List<List<String>>)op.get("rows");int from=rows.size();var mapping=columns.stream().map(c->(String)c.get("id")).toList();
    var appended=WorkbookMaintenance.rows(book,sheetId,incoming,mapping,"append","keep");book=WorkbookMaintenance.extend(book,appended,sheetId,from);var after=WorkbookRules.data(book,sheetId);
    cells=(long)incoming.size()*columns.size();for(int r=from;r<from+incoming.size();r++){touched.add(r);for(int c=0;c<columns.size()&&samples.size()<8;c++)samples.add(Map.of("address",ExcelCells.ref(r+1,c),"before","(새 행)","after",DataReport.rows(after).get(r).get(c)));}
   }else if(kind.equals("ADD_CALCULATED_COLUMN")){
    book=CalculatedColumn.build(book,Map.of("sheetId",sheetId,"label",str(op,"label"),"formula",str(op,"formula"),"scope","ALL"));var after=WorkbookRules.data(book,sheetId);int c=DataReport.columns(after).size()-1;cells=rows.size();for(int r=0;r<rows.size();r++){touched.add(r);if(samples.size()<8)samples.add(Map.of("address",ExcelCells.ref(r+1,c),"before","","after",DataReport.rows(after).get(r).get(c)));}
   }else if(kind.equals("ADD_COLUMN")){
    if(columns.stream().anyMatch(c->str(op,"label").equals(c.get("label"))))throw Problem.invalid("같은 이름의 열이 있습니다: "+str(op,"label"));
    String cid=str(op,"columnId");if(cid.isBlank())cid="ai_"+UUID.nameUUIDFromBytes((sheetId+index+op).getBytes(StandardCharsets.UTF_8)).toString().replace("-","").substring(0,24);
    columns.add(new LinkedHashMap<>(Map.of("id",cid,"label",str(op,"label"),"type",str(op,"type"),"unit","","width",160)));for(int r=0;r<rows.size();r++){rows.get(r).add(str(op,"value"));touched.add(r);if(samples.size()<8)samples.add(Map.of("address",ExcelCells.ref(r+1,columns.size()-1),"before","","after",str(op,"value")));}cells=rows.size();expandStyles(data);book=WorkbookRules.replace(book,sheetId,data);
   }else if(kind.equals("DELETE_ROWS")){
    String range=str(op,"range");if(!range.matches("[1-9][0-9]{0,4}:[1-9][0-9]{0,4}"))throw Problem.invalid("삭제 행은 Excel 행 번호로 2:4처럼 지정하세요. 제목 행은 삭제할 수 없습니다.");String[] pair=range.split(":");int from=Integer.parseInt(pair[0])-2,to=Integer.parseInt(pair[1])-2;if(from<0||to<from||to>=rows.size())throw Problem.invalid("삭제할 행 범위를 확인하세요.");
    for(int r=from;r<=to;r++){touched.add(r);for(int c=0;c<columns.size()&&samples.size()<8;c++)samples.add(Map.of("address",ExcelCells.ref(r+1,c),"before",rows.get(r).get(c),"after","(행 삭제)"));}cells=(long)(to-from+1)*columns.size();var order=java.util.stream.IntStream.range(0,rows.size()).filter(r->r<from||r>to).boxed().toList();book=WorkbookStructure.apply(book,sheetId,order,null,null,false);
   }else{
    int[] bounds=range(str(op,"range"),rows.size(),columns.size(),Set.of("CLEAR_ERRORS","PAUSE_ERRORS").contains(kind));if(kind.equals("SET_CELL")&&(bounds[0]!=bounds[2]||bounds[1]!=bounds[3]))throw Problem.invalid("값 입력은 한 개 셀 주소로 요청하세요.");
    for(int r=bounds[0];r<=bounds[2];r++)for(int c=bounds[1];c<=bounds[3];c++){
     String address=ExcelCells.ref(r+1,c),before=rows.get(r).get(c),formula=ExcelCells.<String>map(ExcelCells.meta(data),"formulas").getOrDefault(address,"");String target=str(op,"formula");
     if(Set.of("CLEAR_ERRORS","PAUSE_ERRORS").contains(kind)&&!(target.isBlank()||target.equals("ALL")?ExcelCells.error(before)||brokenRef(data,address,formula):before.equals(target)||target.equals("#REF!")&&brokenRef(data,address,formula)))continue;
     if(columns.get(c).containsKey("formula"))throw Problem.invalid(address+": 전체 열 계산식의 셀은 개별 수정할 수 없습니다. 해당 열 계산식을 먼저 수정하세요.");
     String value=kind.equals("CLEAR_RANGE")?"":str(op,"value");
     if(kind.equals("PAUSE_ERRORS")){
      var issue=ExcelCells.<Map<String,Object>>map(ExcelCells.meta(data),"issues").getOrDefault(address,Map.of());if(formula.isBlank()||Set.of("DEPENDENCY","TEMPORARY_DEPENDENCY").contains(Objects.toString(issue.get("code"),"")))continue;
      var backup=new LinkedHashMap<String,Object>(issue);backup.put("formula",formula);backup.putIfAbsent("cached",before);backup.putIfAbsent("code","CALC_ERROR");backup.putIfAbsent("message","AI 제안 확인 후 임시 처리한 수식입니다. 원본 수식은 보관되어 있습니다.");backup.put("mode",value.isEmpty()?"INPUT_NEEDED":"USER_INPUT");ExcelCells.<Map<String,Object>>map(ExcelCells.meta(data),"paused").put(address,backup);ExcelCells.map(ExcelCells.meta(data),"formulas").remove(address);ExcelCells.map(ExcelCells.meta(data),"issues").remove(address);rows.get(r).set(c,value);
     }else{
      for(String metadata:List.of("formulas","values","paused","issues","blocked"))ExcelCells.map(ExcelCells.meta(data),metadata).remove(address);
      if(kind.equals("SET_CELL")&&value.startsWith("=")){ExcelCells.<String>map(ExcelCells.meta(data),"formulas").put(address,value.substring(1));rows.get(r).set(c,"");}else rows.get(r).set(c,value);
     }
     touched.add(r);cells++;if(samples.size()<8)samples.add(Map.of("address",address,"before",formula.isBlank()?before:"="+formula,"after",value));
    }book=WorkbookRules.replace(book,sheetId,data);
   }
   book=WorkbookCalculation.calculate(book);var after=WorkbookRules.data(book,sheetId);long errors=ExcelCells.map(ExcelCells.meta(after),"issues").size();previews.add(Map.of("kind",kind,"affectedCells",cells,"affectedRows",touched.size(),"samples",samples,"errors",errors));
  }return new Result(book,List.copyOf(previews));
 }
 private static int[] range(String ref,int rows,int cols,boolean all){
  if(all&&ref.equals("ALL"))return new int[]{0,0,rows-1,cols-1};String[] pair=ref.split(":",-1);if(pair.length>2)throw Problem.invalid("셀 범위는 A2 또는 A2:B10 형태입니다.");int[] first=ExcelCells.position(pair[0]),last=ExcelCells.position(pair.length==1?pair[0]:pair[1]);if(first[0]<1||first[1]<0||last[0]<first[0]||last[1]<first[1]||last[0]>rows||last[1]>=cols)throw Problem.invalid("변경할 셀 범위가 실제 데이터 안에 있어야 합니다. 제목 행은 기본정보/열 편집에서 변경하세요.");return new int[]{first[0]-1,first[1],last[0]-1,last[1]};
 }
 @SuppressWarnings("unchecked") private static void expandStyles(Map<String,Object> data){if(data.get("presentation") instanceof Map<?,?> p&&p.get("cellStyles") instanceof List<?> styleRows){var result=new LinkedHashMap<>((Map<String,Object>)p);result.put("cellStyles",styleRows.stream().map(row->{var cells=new ArrayList<>((List<Object>)row);cells.add(0);return cells;}).toList());data.put("presentation",result);}}
 private static boolean brokenRef(Map<String,Object> data,String address,String formula){return ExcelCells.map(ExcelCells.meta(data),"issues").containsKey(address)&&formula.replaceAll("\"(?:[^\"]|\"\")*\"", "\"\"").contains("#REF!");}
 private static String str(Map<String,Object> m,String k){return Objects.toString(m.get(k),"");}
}
