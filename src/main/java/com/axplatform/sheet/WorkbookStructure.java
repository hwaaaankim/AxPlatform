package com.axplatform.sheet;
import com.axplatform.common.Problem;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.formula.*;
import org.apache.poi.ss.formula.ptg.*;
import java.util.*;

/** Remap A1 references to stable row/column identities before a structural mutation. */
public final class WorkbookStructure {
 private WorkbookStructure(){}
 @SuppressWarnings("unchecked") public static Map<String,Object> apply(Map<String,Object> raw,String sheetId,List<Integer> rowOrder,List<String> columnOrder,String name,boolean remove){
  var before=WorkbookRules.normalize(raw);var target=WorkbookRules.sheet(before,sheetId);var old=WorkbookRules.data(before,sheetId);var columns=(List<Map<String,Object>>)old.get("columns");var rows=(List<List<String>>)old.get("rows");
  if(remove&&WorkbookRules.sheets(before).size()==1)throw Problem.invalid("마지막 시트는 삭제할 수 없습니다.");
  var rr=rowOrder==null?java.util.stream.IntStream.range(0,rows.size()).boxed().toList():rowOrder;
  var cc=columnOrder==null?java.util.stream.IntStream.range(0,columns.size()).boxed().toList():columnOrder.stream().map(id->{for(int i=0;i<columns.size();i++)if(id.equals(columns.get(i).get("id")))return i;return -1;}).toList();
  if(new HashSet<>(rr).size()!=rr.size()||rr.stream().anyMatch(i->i==null||i<0||i>=rows.size())||new HashSet<>(cc).size()!=cc.size()||cc.contains(-1))throw Problem.invalid("행·열 변경 범위를 확인하세요.");
  if(cc.isEmpty())rr=List.of();int[] rm=new int[rows.size()+1],cm=new int[columns.size()];Arrays.fill(rm,-1);Arrays.fill(cm,-1);rm[0]=0;for(int i=0;i<rr.size();i++)rm[rr.get(i)+1]=i+1;for(int i=0;i<cc.size();i++)cm[cc.get(i)]=i;
  String newName=name==null?(String)target.get("name"):WorkbookRules.name(name);if(WorkbookRules.sheets(before).stream().anyMatch(s->!s.get("id").equals(sheetId)&&newName.equalsIgnoreCase((String)s.get("name"))))throw Problem.invalid("같은 이름의 시트가 있습니다. 다른 이름을 입력하세요.");var result=new LinkedHashMap<>(before);var after=new ArrayList<Map<String,Object>>();
  try(var wb=new HSSFWorkbook()){
   for(var s:WorkbookRules.sheets(before))wb.createSheet((String)s.get("name"));var context=HSSFEvaluationWorkbook.create(wb);int targetIndex=wb.getSheetIndex((String)target.get("name"));
   var formulaSets=new IdentityHashMap<Map<String,Object>,Map<String,Ptg[]>>();
   for(int si=0;si<WorkbookRules.sheets(before).size();si++){
    var s=WorkbookRules.sheets(before).get(si);if(remove&&s.get("id").equals(sheetId))continue;var item=new LinkedHashMap<>(s);var data=ExcelCells.mutable(WorkbookRules.data(before,(String)s.get("id")));var meta=ExcelCells.meta(data);
    for(String kind:List.of("formulas","paused")){
     var formulas=new LinkedHashMap<String,Ptg[]>();for(var e:ExcelCells.map(meta,kind).entrySet()){
      String f=kind.equals("formulas")?(String)e.getValue():Objects.toString(((Map<?,?>)e.getValue()).get("formula"),"");
      try{if(ExcelCells.map(meta,"blocked").containsKey(e.getKey()))throw Problem.invalid("배열 범위가 있는 수식은 구조를 먼저 수정할 수 없습니다.");WorkbookCalculation.verifySheets(f,wb);var tokens=FormulaParser.parse(f,context,FormulaType.CELL,si,ExcelCells.position(e.getKey())[0]);
       for(int t=0;t<tokens.length;t++){
        var token=tokens[t];int referenced=si;
        if(token instanceof Ref3DPtg ref)referenced=external(context,ref.getExternSheetIndex());
        if(token instanceof Area3DPtg area)referenced=external(context,area.getExternSheetIndex());
        if(referenced!=targetIndex)continue;
        if(token instanceof RefPtgBase ref){int r=map(ref.getRow(),rm,rr.size()+1),c=map(ref.getColumn(),cm,cc.size());if(remove||r<0||c<0)tokens[t]=new RefErrorPtg();else{ref.setRow(r);ref.setColumn(c);}}
        else if(token instanceof AreaPtgBase area){int[] r=range(area.getFirstRow(),area.getLastRow(),rm,rr.size()+1),c=range(area.getFirstColumn(),area.getLastColumn(),cm,cc.size());if(remove||r==null||c==null)tokens[t]=new AreaErrPtg();else{area.setFirstRow(r[0]);area.setLastRow(r[1]);area.setFirstColumn(c[0]);area.setLastColumn(c[1]);}}
       }formulas.put(e.getKey(),tokens);
      }catch(RuntimeException error){throw Problem.invalid(s.get("name")+"!"+e.getKey()+": 수식 참조를 안전하게 옮길 수 없습니다. 해당 수식을 지원하는 새 수식으로 교체한 뒤 구조를 변경하세요.");}
     }
     var holder=new LinkedHashMap<String,Object>();holder.put("meta",meta);holder.put("kind",kind);formulaSets.put(holder,formulas);
    }
    item.put("data",data);after.add(item);
   }
   wb.setSheetName(targetIndex,newName);
   for(var e:formulaSets.entrySet()){
    var meta=(Map<String,Object>)e.getKey().get("meta");String kind=(String)e.getKey().get("kind");var map=ExcelCells.map(meta,kind);
    for(var f:e.getValue().entrySet()){String translated=FormulaRenderer.toFormulaString(context,f.getValue());if(kind.equals("formulas"))map.put(f.getKey(),translated);else{var backup=new LinkedHashMap<>((Map<String,Object>)map.get(f.getKey()));backup.put("formula",translated);map.put(f.getKey(),backup);}}
   }
   if(!remove){var item=after.stream().filter(s->sheetId.equals(s.get("id"))).findFirst().orElseThrow();item.put("name",newName);var data=(Map<String,Object>)item.get("data");var meta=ExcelCells.meta(data);
    for(String kind:List.of("formulas","values","paused","issues","blocked")){var relocated=new LinkedHashMap<String,Object>();for(var e:ExcelCells.map(meta,kind).entrySet()){var at=ExcelCells.position(e.getKey());int r=rm[at[0]],c=cm[at[1]];if(r>=0&&c>=0)relocated.put(ExcelCells.ref(r,c),e.getValue());}meta.put(kind,relocated);}
    data.put("columns",cc.stream().map(columns::get).toList());data.put("rows",rr.stream().map(r->cc.stream().map(c->rows.get(r).get(c)).toList()).toList());var ids=(List<String>)old.get("rowIds");data.put("rowIds",rr.stream().map(ids::get).toList());data.remove("view");
    if(old.get("presentation") instanceof Map<?,?> p){var presentation=new LinkedHashMap<String,Object>((Map<String,Object>)p);var styles=(List<List<Integer>>)p.get("cellStyles");if(styles!=null){var mapped=new ArrayList<List<Integer>>();mapped.add(cc.stream().map(c->styles.get(0).get(c)).toList());for(int r:rr)mapped.add(cc.stream().map(c->styles.get(r+1).get(c)).toList());presentation.put("cellStyles",mapped);}var merges=new ArrayList<List<Integer>>();for(var m:(List<List<Integer>>)p.getOrDefault("merges",null)){int r0=rm[m.get(0)],r1=rm[m.get(2)],c0=cm[m.get(1)],c1=cm[m.get(3)];if(r0>=0&&c0>=0&&r1-r0==m.get(2)-m.get(0)&&c1-c0==m.get(3)-m.get(1))merges.add(List.of(r0,c0,r1,c1));}presentation.put("merges",merges);data.put("presentation",presentation);}
   }
  }catch(java.io.IOException e){throw Problem.invalid("시트 구조 변경을 정리하지 못했습니다.");}
  result.put("sheets",after);if(remove&&sheetId.equals(result.get("activeSheetId")))result.put("activeSheetId",after.get(0).get("id"));return WorkbookCalculation.calculate(result);
 }
 private static int external(HSSFEvaluationWorkbook c,int id){String first=c.getSheetFirstNameByExternSheet(id),last=c.getSheetLastNameByExternSheet(id);if(!Objects.equals(first,last))throw Problem.invalid("여러 시트를 묶은 3차원 참조는 구조 변경 전에 단일 시트 참조로 나누세요.");return c.getSheetIndex(first);}
 private static int map(int at,int[] mapping,int newSize){return at<mapping.length?mapping[at]:at+newSize-mapping.length;}
 private static int[] range(int start,int end,int[] mapping,int newSize){var values=new TreeSet<Integer>();for(int i=start;i<=end;i++){int n=map(i,mapping,newSize);if(n>=0)values.add(n);}if(values.isEmpty())return null;if(values.last()-values.first()+1!=values.size())throw Problem.invalid("참조 범위가 떨어져 나뉩니다.");return new int[]{values.first(),values.last()};}
}
