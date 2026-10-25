package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.util.*;

/** Adds one explicit formula column; never changes source inputs. Preview and commit use the same path. */
public final class CalculatedColumn {
 private CalculatedColumn(){}
 @SuppressWarnings("unchecked") public static Map<String,Object> build(Map<String,Object> original,Map<String,Object> spec){
  if(spec==null||!Set.of("sheetId","label","formula","scope","view").containsAll(spec.keySet()))throw Problem.invalid("계산 열 설정을 확인하세요.");
  String id=DataReport.str(spec,"sheetId","sheet1"),label=DataReport.str(spec,"label","").strip(),formula=DataReport.str(spec,"formula","").strip(),scope=DataReport.str(spec,"scope","ALL");
  if(formula.startsWith("="))formula=formula.substring(1);if(label.isEmpty()||label.length()>100||formula.isEmpty()||formula.length()>4000||!Set.of("ALL","FILTERED").contains(scope))throw Problem.invalid("새 열 이름과 수식을 입력하세요.");
  var book=WorkbookRules.normalize(original);var entry=WorkbookRules.sheet(book,id);var data=ExcelCells.mutable((Map<String,Object>)entry.get("data"));var columns=DataReport.columns(data);var rows=DataReport.rows(data);
  if(columns.size()>=100||(columns.size()+1L)*rows.size()>1_000_000)throw Problem.invalid("계산 열 추가 시 100열 또는 100만 셀 한도를 초과합니다.");
  if(columns.stream().anyMatch(c->label.equals(c.get("label"))))throw Problem.invalid("같은 이름의 열이 있습니다. 다른 열 이름을 입력하세요.");
  if(rows.isEmpty())throw Problem.invalid("계산할 데이터 행이 없습니다. 편집에서 값을 먼저 입력하세요.");
  List<Integer> target=java.util.stream.IntStream.range(0,rows.size()).boxed().toList();
  if(scope.equals("FILTERED")){if(!(spec.get("view") instanceof Map<?,?> v))throw Problem.invalid("현재 조회 조건이 없습니다.");var view=DataReport.config((Map<String,Object>)v);if(!id.equals(view.get("sheetId")))throw Problem.invalid("계산 열과 조회 조건의 시트가 다릅니다.");target=DataReport.matching(data,view);}
  if(target.isEmpty())throw Problem.invalid("조건에 맞는 행이 없습니다. 조회 조건을 변경하세요.");
  int ci=columns.size();String columnId="calc_"+UUID.randomUUID().toString().replace("-","").substring(0,18);columns.add(new LinkedHashMap<>(Map.of("id",columnId,"label",label,"type","TEXT","unit","","width",160)));
  rows.forEach(row->row.add(""));var formulas=ExcelCells.<String>map(ExcelCells.meta(data),"formulas");for(int r:target)formulas.put(ExcelCells.ref(r+1,ci),formula.replace("{row}",String.valueOf(r+2)));
  if(data.get("presentation") instanceof Map<?,?> p&&p.get("cellStyles") instanceof List<?> styles){var visual=new LinkedHashMap<>((Map<String,Object>)p);visual.put("cellStyles",styles.stream().map(row->{var values=new ArrayList<>((List<Object>)row);while(values.size()<ci+1)values.add(0);return values;}).toList());data.put("presentation",visual);}
  book=WorkbookCalculation.calculate(WorkbookRules.replace(book,id,data));var computed=ExcelCells.mutable(WorkbookRules.data(book,id));if(DataReport.rows(computed).stream().allMatch(row->row.get(ci).isEmpty()||ExcelCells.error(row.get(ci))||DataReport.number(row.get(ci))!=null)){DataReport.columns(computed).get(ci).put("type","NUMBER");}return WorkbookRules.replace(book,id,computed);
 }
 public static Map<String,Object> preview(Map<String,Object> book,String sheetId,long version,long draftVersion){var data=WorkbookRules.data(book,sheetId);var cols=DataReport.columns(data);int ci=cols.size()-1;var rows=DataReport.rows(data);var samples=new ArrayList<Map<String,Object>>();int bad=0;
  for(int r=0;r<rows.size();r++){String ref=ExcelCells.ref(r+1,ci);if(DataReport.uncertain(data,r,ci))bad++;if(samples.size()<10&&ExcelCells.hasFormula(data,ref))samples.add(Map.of("address",ref,"value",rows.get(r).get(ci),"formula",ExcelCells.map(ExcelCells.meta(data),"formulas").get(ref),"issue",ExcelCells.map(ExcelCells.meta(data),"issues").getOrDefault(ref,Map.of())));}
  int formulas=0;for(int r=0;r<rows.size();r++)if(ExcelCells.hasFormula(data,ExcelCells.ref(r+1,ci)))formulas++;
  return Map.of("sheetVersion",version,"draftVersion",draftVersion,"column",cols.get(ci),"samples",samples,"errorCount",bad,"affectedRows",formulas);
 }
}
