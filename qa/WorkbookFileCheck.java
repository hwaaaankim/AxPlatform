import java.nio.file.*;import java.util.*;import com.axplatform.sheet.*;import com.axplatform.common.*;
class WorkbookFileCheck {
 public static void main(String[] args)throws Exception{
  long start=System.nanoTime();var report=SheetImport.readWorkbook(Files.readAllBytes(Path.of(args[0])),"input.xlsx","UTF-8");
  var entries=new ArrayList<Map<String,Object>>();int index=0;
  for(var s:(List<Map<String,Object>>)report.get("sheets")){
   var headers=(List<String>)s.get("headers");var rows=(List<List<String>>)s.get("rows");System.out.println(s.get("sheetName")+" status="+s.get("status")+" columns="+headers.size()+" rows="+rows.size()+" issues="+s.get("issues"));
   var cols=new ArrayList<Map<String,Object>>();for(int i=0;i<headers.size();i++)cols.add(Map.of("id","c"+i,"label",headers.get(i).isBlank()?SheetFormula.letter(i):headers.get(i).substring(0,Math.min(100,headers.get(i).length())),"type","TEXT","unit","","width",160,"sourceHeader",headers.get(i)));
   var data=new LinkedHashMap<String,Object>();data.put("columns",cols);data.put("rows",rows);data.put("rowIds",java.util.stream.IntStream.range(0,rows.size()).mapToObj(r->"r"+r).toList());data.put("presentation",s.get("presentation"));if(s.containsKey("excel"))data.put("excel",s.get("excel"));
   entries.add(Map.of("id","sheet"+index++,"name",s.get("sheetName"),"data",data,"sourceAssetIds",List.of()));
  }
  var book=WorkbookRules.normalize(Map.of("format","AX_WORKBOOK_1","activeSheetId","sheet0","sheets",entries));var json=new JsonCodec();String encoded=json.write(book);System.out.println("book UTF8 bytes="+encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+" timeMs="+(System.nanoTime()-start)/1000000);
  Files.writeString(Path.of(args[1]),json.write(report));
 }
}
