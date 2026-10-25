package com.axplatform.sheet;

import com.axplatform.common.Problem;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.formula.*;
import org.apache.poi.ss.formula.ptg.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.SpreadsheetVersion;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;

/** Local formula evaluation only. No external workbooks, macros, network functions or UDFs. */
public final class WorkbookCalculation {
 private WorkbookCalculation(){}
 private static final java.util.concurrent.Semaphore SLOTS=new java.util.concurrent.Semaphore(2);
 private static final java.util.regex.Pattern EXTERNAL=java.util.regex.Pattern.compile("(?i)(\\[[^]]+\\]|(?:WEBSERVICE|RTD|CUBEVALUE|CUBEMEMBER|CALL|REGISTER\\.ID)\\s*\\(|\\|)");
 public static Map<String,Object> calculate(Map<String,Object> raw){
  var normalized=WorkbookRules.normalize(raw);if(WorkbookRules.sheets(normalized).stream().noneMatch(s->ExcelCells.hasAny(WorkbookRules.data(normalized,(String)s.get("id")))))return normalized;
  long count=0;for(var sheet:WorkbookRules.sheets(normalized)){var data=WorkbookRules.data(normalized,(String)sheet.get("id"));count+=ExcelCells.map(ExcelCells.meta(data),"formulas").size();for(var column:(List<?>)data.get("columns"))if(((Map<?,?>)column).containsKey("formula"))count+=((List<?>)data.get("rows")).size();}if(count>200_000)throw Problem.invalid("통합문서 수식은 최대 20만 셀까지 계산합니다. 프로젝트를 나누세요.");
  if(!SLOTS.tryAcquire())throw new Problem(429,"CALC_BUSY","다른 표를 계산 중입니다. 입력은 보존됩니다. 잠시 후 다시 계산하세요.");
  try{return evaluate(normalized);}finally{SLOTS.release();}
 }
 @SuppressWarnings("unchecked") private static Map<String,Object> evaluate(Map<String,Object> book){
  long deadline=System.nanoTime()+30_000_000_000L;var result=new LinkedHashMap<>(book);var sheets=new ArrayList<Map<String,Object>>();
  for(var s:WorkbookRules.sheets(book)){var item=new LinkedHashMap<>(s);item.put("data",ExcelCells.mutable(WorkbookRules.data(book,(String)s.get("id"))));sheets.add(item);}result.put("sheets",sheets);
  try(var wb=new HSSFWorkbook()){
   for(var s:sheets)wb.createSheet((String)s.get("name"));
   var problems=new ArrayList<Map<String,Map<String,Object>>>();var oldIssues=new ArrayList<Map<String,Map<String,Object>>>();
   for(int si=0;si<sheets.size();si++){
    var data=(Map<String,Object>)sheets.get(si).get("data");var meta=ExcelCells.meta(data);var issue=new LinkedHashMap<String,Map<String,Object>>();problems.add(issue);oldIssues.add(new LinkedHashMap<>(ExcelCells.map(meta,"issues")));meta.put("issues",issue);
    var sheet=wb.getSheetAt(si);var columns=(List<Map<String,Object>>)data.get("columns");var rows=(List<List<String>>)data.get("rows");
    for(int r=0;r<=rows.size();r++)for(int c=0;c<columns.size();c++){
     String address=ExcelCells.ref(r,c),v=ExcelCells.value(data,address);if(v.isEmpty()&&!ExcelCells.hasFormula(data,address)&&!ExcelCells.map(meta,"paused").containsKey(address))continue;
     Cell cell=cell(sheet,r,c);setValue(cell,v,meta,address,columns.get(c));
     var nativeValue=ExcelCells.<List<String>>map(meta,"values").get(address);if(!ExcelCells.hasFormula(data,address)&&nativeValue!=null&&nativeValue.get(2).equals("e")&&nativeValue.get(1).equals(v))issue.put(address,problem("",v,"INPUT_ERROR","원본 셀의 "+v+" 오류입니다. 해당 셀의 값을 직접 입력하세요."));
     var paused=ExcelCells.<Map<String,Object>>map(meta,"paused").get(address);
     if(paused!=null&&v.isEmpty())cell.setCellErrorValue(FormulaError.NA.getCode());
    }
   }
   var allFormulas=new ArrayList<Map<String,String>>();for(var s:sheets)allFormulas.add(formulas((Map<String,Object>)s.get("data")));
   var dependencies=new HashMap<String,Set<String>>();var known=new HashSet<String>();
   for(int si=0;si<sheets.size();si++){var m=ExcelCells.meta((Map<String,Object>)sheets.get(si).get("data"));for(String ref:allFormulas.get(si).keySet())known.add(si+"!"+ref);for(String ref:ExcelCells.map(m,"paused").keySet())known.add(si+"!"+ref);for(String ref:ExcelCells.map(m,"issues").keySet())known.add(si+"!"+ref);}
   boolean mixedDates=sheets.stream().map(s->Boolean.TRUE.equals(ExcelCells.meta((Map<String,Object>)s.get("data")).get("date1904"))).distinct().count()>1;
   long areaBudget=0;
   for(int si=0;si<sheets.size();si++){
    var data=(Map<String,Object>)sheets.get(si).get("data");var meta=ExcelCells.meta(data);var sheet=wb.getSheetAt(si);
    for(var entry:allFormulas.get(si).entrySet()){
     String address=entry.getKey(),f=entry.getValue();var at=ExcelCells.position(address);Cell cell=cell(sheet,at[0],at[1]);
     try{
      if(ExcelCells.map(meta,"blocked").containsKey(address))throw new UnsupportedOperationException(Objects.toString(ExcelCells.map(meta,"blocked").get(address)));
      if(Boolean.TRUE.equals(meta.get("date1904"))&&java.util.regex.Pattern.compile("(?i)(?:DATE|DATEVALUE|DAY|DAYS|DAYS360|MONTH|YEAR|YEARFRAC|TODAY|NOW|WEEKDAY|WEEKNUM|ISOWEEKNUM|EDATE|EOMONTH|WORKDAY|NETWORKDAYS|XIRR|XNPV)\\s*\\(").matcher(withoutStrings(f)).find())throw new UnsupportedOperationException("1904 날짜 체계의 날짜 함수는 이 계산기에서 지원하지 않습니다. 원본 Excel 계산값을 임시 사용하거나 1900 날짜 체계 파일로 변환하세요.");
      if(EXTERNAL.matcher(withoutStrings(f)).find())throw new UnsupportedOperationException("외부 파일·표 구조 참조 또는 외부 연동 함수는 이 편집기에서 계산할 수 없습니다.");
      if(withoutStrings(f).contains("_xlfn.")||withoutStrings(f).contains("_xlws.")||withoutStrings(f).contains("{")||withoutStrings(f).contains("}"))throw new UnsupportedOperationException("동적 배열·확장 함수·배열 수식은 원본 Excel에서 계산하거나 일반 수식으로 바꿔 주세요.");
      if(mixedDates&&withoutStrings(f).contains("!"))throw new UnsupportedOperationException("1900/1904 날짜 체계가 서로 다른 시트를 참조합니다. 원본 파일의 날짜 체계를 통일한 뒤 가져오세요.");
      verifySheets(f,wb);
      var tokens=FormulaParser.parse(f,HSSFEvaluationWorkbook.create(wb),FormulaType.CELL,si,at[0]);
      for(var token:tokens)if(token instanceof AreaPtgBase area){long cost=(long)(area.getLastRow()-area.getFirstRow()+1)*(area.getLastColumn()-area.getFirstColumn()+1);areaBudget+=cost;if(cost>1_000_000||areaBudget>50_000_000)throw new UnsupportedOperationException("계산 참조 범위가 큽니다. 전체 열 대신 실제 데이터 범위를 지정하세요.");}
      collectDependencies(tokens,HSSFEvaluationWorkbook.create(wb),si,si+"!"+address,known,dependencies);
      cell.setCellFormula(f);
     }catch(RuntimeException error){problems.get(si).put(address,problem(f,cached(data,address,oldIssues.get(si)),"UNSUPPORTED",reason(error)));cell.setCellErrorValue(FormulaError.NAME.getCode());}
    }
   }
   for(String key:cyclicDependencies(known,dependencies)){
    int split=key.indexOf('!'),si=Integer.parseInt(key.substring(0,split));String address=key.substring(split+1),f=allFormulas.get(si).get(address);if(f==null)continue;var d=(Map<String,Object>)sheets.get(si).get("data");problems.get(si).put(address,problem(f,cached(d,address,oldIssues.get(si)),"CIRCULAR_REFERENCE","순환 수식 또는 그 결과를 참조하고 있습니다. 참조 연결을 끊은 뒤 다시 계산하세요."));var at=ExcelCells.position(address);cell(wb.getSheetAt(si),at[0],at[1]).setCellErrorValue(FormulaError.REF.getCode());
   }
   var evaluator=wb.getCreationHelper().createFormulaEvaluator();evaluator.setIgnoreMissingWorkbooks(false);
   for(int si=0;si<sheets.size();si++){
    var data=(Map<String,Object>)sheets.get(si).get("data");var meta=ExcelCells.meta(data);var values=ExcelCells.<List<String>>map(meta,"values");var issue=problems.get(si);var sheet=wb.getSheetAt(si);
    for(var entry:allFormulas.get(si).entrySet()){
     String address=entry.getKey(),f=entry.getValue();var at=ExcelCells.position(address);String previous=cached(data,address,oldIssues.get(si));
     if(issue.containsKey(address)){ExcelCells.put(data,address,"CIRCULAR_REFERENCE".equals(issue.get(address).get("code"))?"#CIRC!":"#NAME?");continue;}
     try{
      if(System.nanoTime()>deadline)throw new UnsupportedOperationException("계산 시간이 30초를 넘었습니다. 참조 범위를 줄인 후 다시 계산하세요.");
      CellValue v=evaluator.evaluate(cell(sheet,at[0],at[1]));String raw="",type="s";
      if(v!=null)switch(v.getCellType()){
       case NUMERIC->{raw=decimal(v.getNumberValue());type="n";}
       case BOOLEAN->{raw=v.getBooleanValue()?"1":"0";type="b";}
       case STRING->raw=v.getStringValue();
       case ERROR->{raw=FormulaError.forInt(v.getErrorValue()).getString();type="e";}
       default->{}
      }
      if(type.equals("e")){if(raw.equals("~CIRCULAR~REF~"))raw="#CIRC!";issue.put(address,problem(f,previous,"CALC_ERROR",errorReason(raw)));}
      var old=values.get(address);String format=old!=null?old.get(3):"";String display=display(raw,type,format,Boolean.TRUE.equals(meta.get("date1904")));
      if(display.length()>SheetRules.MAX_CELL_LENGTH)throw new UnsupportedOperationException("계산 결과가 셀당 2,000자를 초과합니다.");
      ExcelCells.put(data,address,display);if(at[0]==0&&!display.isBlank())((List<Map<String,Object>>)data.get("columns")).get(at[1]).put("label",display.substring(0,Math.min(100,display.length())));
      if(!format.isEmpty()||!type.equals("n"))values.put(address,List.of(raw,display,type,format));else values.remove(address);
     }catch(RuntimeException|StackOverflowError error){issue.put(address,problem(f,previous,"UNSUPPORTED",reason(error)));ExcelCells.put(data,address,"#NAME?");}
    }
   }
   var unresolved=new HashSet<String>();var temporary=new HashSet<String>();
   for(int si=0;si<sheets.size();si++){var d=(Map<String,Object>)sheets.get(si).get("data");for(var e:problems.get(si).entrySet())unresolved.add(si+"!"+e.getKey());for(var e:ExcelCells.<Map<String,Object>>map(ExcelCells.meta(d),"paused").entrySet())if("CACHED".equals(e.getValue().get("mode"))||ExcelCells.value(d,e.getKey()).isEmpty())temporary.add(si+"!"+e.getKey());}
   // Dynamic references cannot be proved independent of a provisional source by an A1 dependency graph.
   var dynamic=java.util.regex.Pattern.compile("(?i)(?:INDIRECT|OFFSET)\\s*\\(");
   for(int si=0;si<sheets.size();si++)for(var f:allFormulas.get(si).entrySet()){
    String key=si+"!"+f.getKey();if((!temporary.isEmpty()||unresolved.size()>1||unresolved.size()==1&&!unresolved.contains(key))&&dynamic.matcher(withoutStrings(f.getValue())).find()){
     var d=(Map<String,Object>)sheets.get(si).get("data");var issue=problems.get(si).get(f.getKey());if(issue==null){problems.get(si).put(f.getKey(),problem(f.getValue(),ExcelCells.value(d,f.getKey()),"TEMPORARY_DEPENDENCY","동적 참조와 미확정 셀이 함께 있어 영향 범위를 확정할 수 없습니다. 문제 입력을 해결할 때까지 그래프 집계에서 제외합니다."));temporary.add(key);}else if(Set.of("CALC_ERROR","UNSUPPORTED").contains(issue.get("code"))&&!Objects.toString(issue.get("message"),"").contains("순환")){issue.put("code","DEPENDENCY");issue.put("message","동적 참조가 있어 다른 문제 셀을 먼저 해결한 후 다시 확인해야 합니다. 원본 수식은 유지합니다.");}
    }
   }
   var affected=new HashSet<String>();var queue=new ArrayDeque<String>();queue.addAll(unresolved);queue.addAll(temporary);var seen=new HashSet<String>();
   while(!queue.isEmpty()){var source=queue.removeFirst();if(!seen.add(source))continue;for(String dependent:dependencies.getOrDefault(source,Set.of())){affected.add(dependent);queue.addLast(dependent);}}
   for(String key:affected){int split=key.indexOf('!'),si=Integer.parseInt(key.substring(0,split));String address=key.substring(split+1);var d=(Map<String,Object>)sheets.get(si).get("data");var issue=problems.get(si).get(address);if(issue!=null){if(!Objects.toString(issue.get("formula"),"").contains("#REF!")&&!Objects.toString(issue.get("message"),"").contains("순환")){issue.put("code","DEPENDENCY");issue.put("message","참조한 셀에 문제가 있거나 임시 처리되어 있습니다. 원인 셀을 수정하면 이 수식을 유지한 채 다시 계산합니다.");}}else{String f=allFormulas.get(si).get(address);if(f!=null)problems.get(si).put(address,problem(f,ExcelCells.value(d,address),"TEMPORARY_DEPENDENCY","오류·임시 처리 셀을 참조하는 결과입니다. 입력이 확정되면 다시 계산하며, 현재 그래프 집계에서는 제외합니다."));}}
   for(int si=0;si<sheets.size();si++){var data=(Map<String,Object>)sheets.get(si).get("data");for(var issue:problems.get(si).entrySet())if(!ExcelCells.hasFormula(data,issue.getKey())&&!"INPUT_ERROR".equals(issue.getValue().get("code"))){issue.getValue().put("code","COLUMN_FORMULA");issue.getValue().put("message",issue.getValue().get("message")+" 전체 열 계산식은 열 속성에서 수정하세요.");}}

   return WorkbookRules.normalize(result);
  }catch(java.io.IOException e){throw Problem.invalid("수식 계산 메모리를 정리하지 못했습니다. 다시 시도하세요.");}
 }
 @SuppressWarnings("unchecked") private static Map<String,String> formulas(Map<String,Object> data){
  var result=new LinkedHashMap<>(ExcelCells.<String>map(ExcelCells.meta(data),"formulas"));var columns=(List<Map<String,Object>>)data.get("columns");var rows=(List<?>)data.get("rows");var index=new HashMap<String,Integer>();for(int c=0;c<columns.size();c++)index.put((String)columns.get(c).get("id"),c);
  for(int c=0;c<columns.size();c++)if(columns.get(c).get("formula") instanceof String expression){var refs=SheetFormula.references(expression);for(int r=0;r<rows.size();r++){int excelRow=r+2;String f=SheetFormula.excel(expression,index,excelRow);if(!refs.isEmpty())f="IF(COUNT("+String.join(",",refs.stream().map(id->SheetFormula.letter(index.get(id))+excelRow).toList())+")<"+refs.size()+",\"\","+f+")";result.put(ExcelCells.ref(r+1,c),f);}}
  return result;
 }
 private static Set<String> cyclicDependencies(Set<String> nodes,Map<String,Set<String>> reverse){
  var incoming=new HashMap<String,Integer>();for(String n:nodes)incoming.put(n,0);for(var dependents:reverse.values())for(String n:dependents)incoming.merge(n,1,Integer::sum);var ready=new ArrayDeque<String>();incoming.forEach((k,v)->{if(v==0)ready.addLast(k);});while(!ready.isEmpty()){String n=ready.removeFirst();incoming.remove(n);for(String parent:reverse.getOrDefault(n,Set.of()))if(incoming.containsKey(parent)&&incoming.merge(parent,-1,Integer::sum)==0)ready.addLast(parent);}return incoming.keySet();
 }
 static void verifySheets(String formula,HSSFWorkbook workbook){
  String clean=withoutStrings(formula).replaceAll("#(?:REF!|DIV/0!|VALUE!|NAME\\?|N/A|NUM!|NULL!)", "");
  var names=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])(?:'((?:[^']|'')+)'|([\\p{L}\\p{N}_./\\\\]+))!").matcher(clean);
  while(names.find()){String name=names.group(1)!=null?names.group(1).replace("''","'"):names.group(2);if(name.contains(":"))throw new UnsupportedOperationException("여러 시트를 한 범위로 묶는 참조는 개별 시트 참조로 나누세요.");if(workbook.getSheetIndex(name)<0)throw new UnsupportedOperationException("참조한 ‘"+name+"’ 시트가 현재 통합문서에 없습니다. 필요한 시트를 함께 가져오거나 수식 참조를 수정하세요.");}
 }
 private static void collectDependencies(Ptg[] tokens,HSSFEvaluationWorkbook wb,int current,String dependent,Set<String> known,Map<String,Set<String>> reverse){
  for(var t:tokens){int sheet=current;if(t instanceof Ref3DPtg ref)sheet=wb.convertFromExternSheetIndex(ref.getExternSheetIndex());if(t instanceof Area3DPtg area)sheet=wb.convertFromExternSheetIndex(area.getExternSheetIndex());if(sheet<0)continue;
   if(t instanceof RefPtgBase ref){String key=sheet+"!"+ExcelCells.ref(ref.getRow(),ref.getColumn());if(known.contains(key))reverse.computeIfAbsent(key,k->new HashSet<>()).add(dependent);}
   else if(t instanceof AreaPtgBase area){for(int r=area.getFirstRow();r<=Math.min(SheetRules.MAX_ROWS,area.getLastRow());r++)for(int c=area.getFirstColumn();c<=Math.min(SheetRules.MAX_COLUMNS-1,area.getLastColumn());c++){String key=sheet+"!"+ExcelCells.ref(r,c);if(known.contains(key))reverse.computeIfAbsent(key,k->new HashSet<>()).add(dependent);}}
  }
 }
 private static Cell cell(Sheet s,int r,int c){Row row=s.getRow(r);if(row==null)row=s.createRow(r);Cell cell=row.getCell(c);return cell==null?row.createCell(c):cell;}
 private static void setValue(Cell cell,String value,Map<String,Object> meta,String address,Map<String,Object> column){
  var nativeValue=ExcelCells.<List<String>>map(meta,"values").get(address);String type="",raw=value;
  if(nativeValue!=null&&nativeValue.get(1).equals(value)){raw=nativeValue.get(0);type=nativeValue.get(2);}
  try{
   if(type.equals("e")||!type.equals("s")&&ExcelCells.error(raw)){cell.setCellErrorValue(raw.equals("#CIRC!")?FormulaError.CIRCULAR_REF.getCode():FormulaError.forString(raw).getCode());return;}
   if(type.equals("b")){cell.setCellValue(raw.equals("1"));return;}
   if(!type.equals("s")&&(type.equals("d")||"DATE".equals(column.get("type"))||nativeValue!=null&&nativeValue.get(3).matches("(?i).*[yd].*"))&&value.matches("\\d{4}-\\d{2}-\\d{2}(T.*)?")){
    if(type.equals("n")){cell.setCellValue(Double.parseDouble(raw));return;}
    var date=LocalDate.parse(value.substring(0,10));double serial=DateUtil.getExcelDate(date);if(Boolean.TRUE.equals(meta.get("date1904")))serial-=1462;cell.setCellValue(serial);return;
   }
   if(!type.equals("s")&&raw.matches("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")){double n=Double.parseDouble(raw);if(Double.isFinite(n)){cell.setCellValue(n);return;}}
  }catch(RuntimeException ignored){}
  if(value.isEmpty())cell.setBlank();else cell.setCellValue(value);
 }
 private static String decimal(double value){if(!Double.isFinite(value))return "#NUM!";return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
 static String display(String raw,String type,String format,boolean date1904){if(type.equals("b"))return raw.equals("1")?"TRUE":"FALSE";if(!type.equals("n")||format.isEmpty())return raw;return SheetImport.formatted(raw,format,date1904,"수식 결과");}
 private static String withoutStrings(String formula){return formula.replaceAll("\"(?:[^\"]|\"\")*\"", "\"\"");}
 private static String cached(Map<String,Object> data,String address,Map<String,Map<String,Object>> prior){String current=ExcelCells.value(data,address);var p=prior.get(address);return p!=null&&ExcelCells.error(current)?(Objects.equals(p.get("formula"),ExcelCells.map(ExcelCells.meta(data),"formulas").get(address))?Objects.toString(p.get("cached"),""):""):current;}
 private static Map<String,Object> problem(String f,String cached,String code,String message){return new LinkedHashMap<>(Map.of("formula",f,"cached",cached,"code",code,"message",message));}
 private static String reason(Throwable error){if(error instanceof UnsupportedOperationException)return error.getMessage();String name=error.getClass().getSimpleName();if(name.contains("NotImplemented"))return "이 함수 또는 참조 방식은 아직 계산을 지원하지 않습니다. 마지막 저장값을 임시 사용하거나 수식을 수정하세요.";if(error instanceof StackOverflowError)return "참조 연결이 너무 깊거나 순환합니다. 참조 셀을 확인하세요.";return "함수 이름·참조 시트·수식 문법을 확인하세요. 이름 정의, 구조화 참조, 확장 함수는 지원 범위를 벗어날 수 있습니다.";}
 private static String errorReason(String error){return switch(error){case "#DIV/0!"->"나누는 값이 0 또는 빈칸입니다. 분모 데이터를 입력하거나 IFERROR 조건을 추가하세요.";case "#REF!"->"참조 셀·시트가 없거나 삭제되었습니다. 참조 범위를 다시 선택하세요.";case "#VALUE!"->"숫자 계산에 문자 등 맞지 않는 값이 들어 있습니다. 입력 셀 형식을 확인하세요.";case "#N/A"->"찾는 값 또는 필요한 입력이 없습니다. 원본 데이터·검색 범위를 확인하세요.";case "#CIRC!","~CIRCULAR~REF~"->"자기 자신을 다시 참조하는 순환 수식입니다. 참조 연결을 수정하세요.";default->"계산 결과가 "+error+"입니다. 원본 수식과 입력 데이터를 확인하세요.";};}
 /** Reviewable, local-only provisional repair. The original formula is always retained in paused. */
 @SuppressWarnings("unchecked") public static Map<String,Object> repair(Map<String,Object> raw,String action){
  if(!Set.of("repair","restore").contains(action))throw Problem.invalid("수식 처리 방식을 확인하세요.");var book=calculate(raw);var result=new LinkedHashMap<>(book);var sheets=new ArrayList<Map<String,Object>>();
  for(var s:WorkbookRules.sheets(book)){var item=new LinkedHashMap<>(s);var data=ExcelCells.mutable(WorkbookRules.data(book,(String)s.get("id")));var meta=ExcelCells.meta(data);var formulas=ExcelCells.<String>map(meta,"formulas");var paused=ExcelCells.<Map<String,Object>>map(meta,"paused");
   if(action.equals("restore")){for(var e:paused.entrySet())formulas.put(e.getKey(),Objects.toString(e.getValue().get("formula"),""));paused.clear();}
   else for(var e:ExcelCells.<Map<String,Object>>map(meta,"issues").entrySet()){
    var issue=e.getValue();if(Set.of("DEPENDENCY","TEMPORARY_DEPENDENCY").contains(issue.get("code")))continue;String f=formulas.remove(e.getKey());if(f==null)continue;String cache=Objects.toString(issue.get("cached"),"");boolean keep="UNSUPPORTED".equals(issue.get("code"))&&!cache.isEmpty()&&!ExcelCells.error(cache);
    var backup=new LinkedHashMap<>(issue);backup.put("formula",f);backup.put("mode",keep?"CACHED":"INPUT_NEEDED");paused.put(e.getKey(),backup);ExcelCells.put(data,e.getKey(),keep?cache:"");
   }
   item.put("data",data);sheets.add(item);
  }
  result.put("sheets",sheets);return calculate(result);
 }
 /** One parser context per imported workbook, so relative shared formulas retain absolute anchors. */
 public static final class Copies implements AutoCloseable {
  private final HSSFWorkbook wb=new HSSFWorkbook();private final Map<String,Ptg[]> cache=new HashMap<>();
  public Copies(List<String> names){for(String n:names)wb.createSheet(n);}
  public String translate(String formula,String name,int oldRow,int oldCol,int row,int col){
   int index=wb.getSheetIndex(name);var context=HSSFEvaluationWorkbook.create(wb);String key=index+":"+oldRow+":"+formula;
   var tokens=cache.computeIfAbsent(key,k->FormulaParser.parse(formula,context,FormulaType.CELL,index,oldRow));var copy=Arrays.stream(tokens).map(Ptg::copy).toArray(Ptg[]::new);
   if(row!=oldRow)FormulaShifter.createForRowCopy(index,name,oldRow,oldRow,row-oldRow,SpreadsheetVersion.EXCEL97).adjustFormula(copy,index);
   if(col!=oldCol)FormulaShifter.createForColumnCopy(index,name,oldCol,oldCol,col-oldCol,SpreadsheetVersion.EXCEL97).adjustFormula(copy,index);
   return FormulaRenderer.toFormulaString(context,copy);
  }
  public void close(){try{wb.close();}catch(java.io.IOException ignored){}}
 }
}
