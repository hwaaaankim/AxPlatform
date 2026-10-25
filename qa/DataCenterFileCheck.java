import com.axplatform.sheet.*;import com.axplatform.common.*;import java.util.*;import java.math.*;import java.nio.file.*;
public class DataCenterFileCheck{
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var book=WorkbookRules.normalize(new JsonCodec().object(Files.readString(Path.of(args[0]))));int sheets=0;
  for(var entry:WorkbookRules.sheets(book)){
   var data=(Map<String,Object>)entry.get("data");var rows=(List<List<String>>)data.get("rows");var columns=(List<Map<String,Object>>)data.get("columns");var profile=DataReport.profile(data);var c=new LinkedHashMap<String,Object>(Map.of("format","AX_DATA_VIEW_1","mode","TABLE","name","실제 파일 조회","sheetId",entry.get("id")));
   var table=DataReport.build(data,c);if(table.rows().size()!=rows.size()||!table.rows().get(rows.size()-1).equals(rows.get(rows.size()-1)))throw new AssertionError("source rows missing");
   var dim=profile.stream().filter(p->Objects.toString(p.get("label"),"").equals("월")).findFirst().orElse(profile.get(0));var metric=profile.stream().filter(p->Boolean.TRUE.equals(p.get("numeric"))&&!p.get("id").equals(dim.get("id"))).sorted(Comparator.comparing(p->Objects.toString(p.get("label"),"").matches(".*(금액|수량|중량|입고|출고|단가).*" )?0:1)).findFirst().orElseThrow();
   c.put("mode","REPORT");c.put("dimensions",List.of(dim.get("id")));c.put("metrics",List.of(Map.of("column",metric.get("id"),"operation","SUM"),Map.of("column","","operation","COUNT")));
   var report=DataReport.build(data,c);BigDecimal actual=report.rows().stream().filter(r->!r.get(1).isBlank()).map(r->new BigDecimal(r.get(1))).reduce(BigDecimal.ZERO,BigDecimal::add);int mi=-1,di=-1;for(int i=0;i<columns.size();i++){if(columns.get(i).get("id").equals(metric.get("id")))mi=i;if(columns.get(i).get("id").equals(dim.get("id")))di=i;}
   BigDecimal expected=BigDecimal.ZERO;for(int r=0;r<rows.size();r++){String mr=ExcelCells.ref(r+1,mi),dr=ExcelCells.ref(r+1,di);if(ExcelCells.map(ExcelCells.meta(data),"issues").containsKey(mr)||ExcelCells.map(ExcelCells.meta(data),"issues").containsKey(dr)||rows.get(r).get(mi).isBlank())continue;expected=expected.add(new BigDecimal(rows.get(r).get(mi).replace(",","")));}if(expected.compareTo(actual)!=0)throw new AssertionError("aggregate mismatch");
   System.out.println(entry.get("name")+": "+rows.size()+" rows / "+columns.size()+" columns; grouped by "+dim.get("label")+", sum("+metric.get("label")+") + count; "+report.rows().size()+" groups; totals match source");sheets++;
  }System.out.println("PASS "+sheets+" real workbook sheets: full raw table and independent aggregate verification");
 }
}
