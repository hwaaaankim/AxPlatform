package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.io.*;
import java.math.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.*;
import org.w3c.dom.*;

/** Bounded streaming OOXML reader with local, reviewed formula evaluation. Never fetches external resources. */
public final class SheetImport {
    private SheetImport() { }
    private static final int EXPANDED=128*1024*1024, PART=64*1024*1024;
    private record Style(String format, Map<String,Object> visual) { }
    private record Cell(int row,int col,String value,int style) { }
    public static void validatePackage(byte[] bytes) { var p=unzip(bytes);xml(required(p,"xl/workbook.xml"));required(p,"xl/_rels/workbook.xml.rels"); }
    public static Map<String,Object> read(byte[] bytes,String filename,String encoding,String selected) {
        var report=readWorkbook(bytes,filename,encoding);
        @SuppressWarnings("unchecked") var sheets=(List<Map<String,Object>>)report.get("sheets");
        var sheet=sheets.stream().filter(s->selected==null||selected.isBlank()||selected.equals(s.get("sheetName"))).findFirst().orElseThrow(()->Problem.invalid("선택한 시트가 없습니다."));
        if("BLOCKED".equals(sheet.get("status")))throw Problem.invalid(((List<Map<String,Object>>)sheet.get("issues")).get(0).get("message").toString());
        var out=new LinkedHashMap<>(sheet);out.put("sheetNames",sheets.stream().map(s->s.get("sheetName")).toList());return out;
    }
    public static Map<String,Object> readWorkbook(byte[] bytes,String filename,String encoding) {
        if(bytes.length==0||bytes.length>10*1024*1024)throw Problem.invalid("파일은 0바이트 초과, 10MB 이내로 선택하세요.");
        var results=new ArrayList<Map<String,Object>>();String lower=filename.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".csv")) {
            if(!Set.of("UTF-8","MS949").contains(encoding))throw Problem.invalid("CSV 문자 인코딩을 선택하세요.");
            try {String text=Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                results.add(matrix("CSV",csv(text.replaceFirst("^\\uFEFF","")),List.of(),Map.of(),"visible"));
            }catch(CharacterCodingException e){throw Problem.invalid("CSV 인코딩을 확인하세요. UTF-8 또는 한글 Windows(CP949)를 선택하세요.");}
        } else if(lower.endsWith(".xlsx")) {
            var parts=unzip(bytes);var wb=xml(required(parts,"xl/workbook.xml"));var rel=xml(required(parts,"xl/_rels/workbook.xml.rels"));
            var nodes=wb.getElementsByTagNameNS("*","sheet");if(nodes.getLength()<1||nodes.getLength()>WorkbookRules.MAX_SHEETS)throw Problem.invalid("통합문서는 1~20개 시트까지 가져올 수 있습니다.");
            boolean date1904=false;var props=wb.getElementsByTagNameNS("*","workbookPr");if(props.getLength()>0)date1904=Set.of("1","true").contains(((Element)props.item(0)).getAttribute("date1904"));
            var shared=shared(parts.get("xl/sharedStrings.xml"));var styles=styles(parts.get("xl/styles.xml"));
            var sheetNames=new ArrayList<String>();for(int i=0;i<nodes.getLength();i++)sheetNames.add(((Element)nodes.item(i)).getAttribute("name"));
            try(var copies=new WorkbookCalculation.Copies(sheetNames)){
            long totalCells=0;for(int i=0;i<nodes.getLength();i++) {
                var node=(Element)nodes.item(i);String name=node.getAttribute("name"),visibility=node.hasAttribute("state")?node.getAttribute("state"):"visible";
                try {
                    String rid=node.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id"),path=null;boolean chart=false;
                    var rs=rel.getElementsByTagNameNS("*","Relationship");for(int j=0;j<rs.getLength();j++){var r=(Element)rs.item(j);if(rid.equals(r.getAttribute("Id"))){if("External".equals(r.getAttribute("TargetMode")))throw Problem.invalid("외부 연결 시트는 읽을 수 없습니다.");chart=!r.getAttribute("Type").endsWith("/worksheet");var t=r.getAttribute("Target");path=t.startsWith("/")?t.substring(1):"xl/"+t;}}
                    if(chart){results.add(blocked(name,visibility,"CHART_SHEET","차트 전용 시트는 편집할 셀 데이터가 없습니다."));continue;}
                    if(path==null||path.contains(".."))throw Problem.invalid("시트 연결 경로가 올바르지 않습니다.");
                    var result=worksheet(name,visibility,required(parts,path),shared,styles,date1904,copies);long size=(long)((List<?>)result.get("headers")).size()*((List<?>)result.get("rows")).size();if(totalCells+size>WorkbookRules.MAX_WORKBOOK_CELLS)throw Problem.invalid("프로젝트 합계 200만 셀을 초과하는 시트입니다. 별도 프로젝트에서 가져오세요.");totalCells+=size;results.add(result);
                }catch(Problem e){results.add(blocked(name,visibility,"SHEET_READ_FAILED",e.getMessage()));}
            }
            }
        }else throw Problem.invalid("XLSX 또는 CSV를 선택하세요. 암호화 문서·XLS·XLSM은 일반 XLSX로 저장해 주세요.");
        calculateReport(results);
        return Map.of("fileName",filename,"sheets",results,"maxSheets",WorkbookRules.MAX_SHEETS,"maxColumns",SheetRules.MAX_COLUMNS,"maxRows",SheetRules.MAX_ROWS);
    }

    @SuppressWarnings("unchecked") private static void calculateReport(List<Map<String,Object>> reports){
        var entries=new ArrayList<Map<String,Object>>();int index=0;
        for(var report:reports){if("BLOCKED".equals(report.get("status")))continue;var headers=(List<String>)report.get("headers");var rows=(List<List<String>>)report.get("rows");var columns=new ArrayList<Map<String,Object>>();for(int c=0;c<headers.size();c++)columns.add(new LinkedHashMap<>(Map.of("id","c"+c,"label",headers.get(c).isBlank()?SheetFormula.letter(c):headers.get(c).substring(0,Math.min(100,headers.get(c).length())),"sourceHeader",headers.get(c),"type","TEXT","unit","","width",160)));
            var data=new LinkedHashMap<String,Object>();data.put("columns",columns);data.put("rows",rows);data.put("rowIds",java.util.stream.IntStream.range(0,rows.size()).mapToObj(r->"r"+r).toList());if(report.containsKey("excel"))data.put("excel",report.get("excel"));entries.add(Map.of("id","s"+index++,"name",report.get("sheetName"),"data",data,"sourceAssetIds",List.of()));
        }
        if(entries.isEmpty())return;var calculated=WorkbookCalculation.calculate(Map.of("format","AX_WORKBOOK_1","sheets",entries,"activeSheetId",entries.get(0).get("id")));
        for(var report:reports){if("BLOCKED".equals(report.get("status")))continue;var entry=WorkbookRules.sheets(calculated).stream().filter(s->s.get("name").equals(report.get("sheetName"))).findFirst().orElseThrow();var data=(Map<String,Object>)entry.get("data");report.put("rows",data.get("rows"));report.put("headers",((List<Map<String,Object>>)data.get("columns")).stream().map(c->c.get("sourceHeader")).toList());if(data.containsKey("excel"))report.put("excel",data.get("excel"));}
    }
    // Translate A1 references while leaving string literals, quoted sheet names and function names intact.
    static String translateShared(String formula,int dr,int dc){
        var pattern=java.util.regex.Pattern.compile("\"(?:[^\"]|\"\")*\"|'(?:[^']|'')*'|(?<![A-Za-z0-9_])([\\$]?)([A-Z]{1,3})([\\$]?)([1-9][0-9]{0,6})(?![A-Za-z0-9_!(])");
        var matcher=pattern.matcher(formula);var out=new StringBuffer();while(matcher.find()){if(matcher.group(1)==null)continue;var at=position(matcher.group(2)+matcher.group(4));int r=at[0]+(matcher.group(3).isEmpty()?dr:0),c=at[1]+(matcher.group(1).isEmpty()?dc:0);String next=r<0||c<0?"#REF!":matcher.group(1)+SheetFormula.letter(c)+matcher.group(3)+(r+1);matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(next));}matcher.appendTail(out);return out.toString();
    }
    private static Map<String,Object> blocked(String name,String visibility,String code,String text){return Map.of("sheetName",name,"visibility",visibility,"status","BLOCKED","headers",List.of(),"rows",List.of(),"warnings",List.of(),"issues",List.of(issue(code,"ERROR",text,"해당 시트의 원본 데이터·범위를 확인하세요. 다른 시트는 그대로 가져올 수 있습니다.")));}
    private static XMLStreamReader stream(byte[] data) throws XMLStreamException {
        var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);factory.setXMLResolver((a,b,c,d)->{throw new XMLStreamException("external XML denied");});return factory.createXMLStreamReader(new ByteArrayInputStream(data));
    }
    private static List<String> shared(byte[] data){var out=new ArrayList<String>();if(data==null)return out;try{var x=stream(data);StringBuilder s=null;long total=0;while(x.hasNext()){int e=x.next();if(e==XMLStreamConstants.DTD)throw Problem.invalid("DTD를 포함한 XML은 허용하지 않습니다.");if(e==1){if(x.getLocalName().equals("si"))s=new StringBuilder();else if(x.getLocalName().equals("t")&&s!=null){s.append(x.getElementText());if(s.length()>SheetRules.MAX_CELL_LENGTH)throw Problem.invalid("셀 문자 길이는 2,000자 이하입니다.");}}else if(e==2&&x.getLocalName().equals("si")){total+=s.length();if(out.size()>500000||total>10_000_000)throw Problem.invalid("공유 문자열이 너무 큽니다.");out.add(s.toString());s=null;}}x.close();return out;}catch(XMLStreamException e){throw Problem.invalid("공유 문자열 XML을 읽지 못했습니다.");}}
    private static String attr(XMLStreamReader x,String key){return Objects.toString(x.getAttributeValue(null,key),"");}
    private static int[] position(String ref){if(!ref.matches("[A-Z]{1,3}[1-9][0-9]{0,6}"))throw Problem.invalid("셀 위치를 확인하세요: "+ref);int split=0,c=0;while(split<ref.length()&&Character.isLetter(ref.charAt(split))){c=c*26+ref.charAt(split++)-'A'+1;}return new int[]{Integer.parseInt(ref.substring(split))-1,c-1};}
    private static Map<String,Object> worksheet(String name,String visibility,byte[] bytes,List<String> shared,List<Style> styles,boolean date1904,WorkbookCalculation.Copies copies){
        var blockedFormulas=new LinkedHashMap<String,String>();var inputIssues=new LinkedHashMap<String,Map<String,Object>>();var formulaMap=new LinkedHashMap<String,String>();var nativeValues=new LinkedHashMap<String,List<String>>();var sharedMasters=new HashMap<String,List<String>>();var sharedFollowers=new LinkedHashMap<String,String>();
        var cells=new ArrayList<Cell>();var merges=new ArrayList<List<Integer>>();var widths=new HashMap<Integer,Integer>();var issues=new ArrayList<Map<String,Object>>();int height=0,width=0,physical=0,formulas=0,missing=0,errors=0,drawings=0;var missingSamples=new ArrayList<String>();var errorSamples=new ArrayList<String>();
        try{var x=stream(bytes);while(x.hasNext()){int event=x.next();if(event==XMLStreamConstants.DTD)throw Problem.invalid("DTD를 포함한 XML은 허용하지 않습니다.");if(event!=1)continue;String tag=x.getLocalName();
            if(tag.equals("col")){int min=Integer.parseInt(attr(x,"min")),max=Integer.parseInt(attr(x,"max"));double w=Double.parseDouble(attr(x,"width").isEmpty()?"20":attr(x,"width"));for(int c=min-1;c<Math.min(max,SheetRules.MAX_COLUMNS);c++)widths.put(c,(int)Math.max(80,Math.min(480,Math.round(w*7))));}
            else if(tag.equals("mergeCell")){var pair=attr(x,"ref").split(":");if(pair.length==2){var a=position(pair[0]);var b=position(pair[1]);if(merges.size()>=100000)throw Problem.invalid("병합 범위가 너무 많습니다.");merges.add(List.of(a[0],a[1],b[0],b[1]));}}
            else if(tag.equals("drawing"))drawings++;
            else if(tag.equals("c")){if(++physical>2_000_000)throw Problem.invalid("물리 셀 수가 200만 개를 초과합니다.");String ref=attr(x,"r"),type=attr(x,"t"),s=attr(x,"s"),value="";int style=s.isBlank()?0:Integer.parseInt(s);boolean formula=false,hasValue=false;String formulaText="",formulaKind="",sharedId="";var pos=position(ref);
                while(x.hasNext()){int e=x.next();if(e==2&&x.getLocalName().equals("c"))break;if(e==1){String child=x.getLocalName();if(child.equals("f")){formula=true;formulaKind=attr(x,"t");sharedId=attr(x,"si");formulaText=x.getElementText();}else if(child.equals("v")){hasValue=true;value=x.getElementText();}else if(child.equals("t")){value+=x.getElementText();hasValue=true;}}}
                if(formula){if(Set.of("array","dataTable").contains(formulaKind))blockedFormulas.put(ref,"배열·가상 분석표 수식은 셀 하나의 수식으로 계산할 수 없습니다. 원본 Excel 계산값을 임시 사용하거나 일반 셀 수식으로 바꿔 주세요.");if(formulaText.length()>8192)throw Problem.invalid(ref+": 수식은 8,192자 이하입니다.");if(formulaKind.equals("shared")){if(!formulaText.isEmpty())sharedMasters.put(sharedId,List.of(ref,formulaText));sharedFollowers.put(ref,sharedId);}else formulaMap.put(ref,formulaText.isEmpty()?"#REF!":formulaText);formulas++;if(!hasValue){missing++;if(missingSamples.size()<8)missingSamples.add(ref);value="#N/A";}}
                String rawNumeric=value;
                if(type.equals("s")){try{value=shared.get(Integer.parseInt(value));}catch(RuntimeException e){throw Problem.invalid(ref+": 문자열 참조를 읽지 못했습니다.");}}
                else if(type.equals("b"))value=value.equals("1")?"TRUE":"FALSE";
                if(type.equals("e")){if(!formula)inputIssues.put(ref,Map.of("formula","","cached",value,"code","INPUT_ERROR","message","원본 셀에 "+value+" 오류가 저장되어 있습니다. 값을 직접 수정하거나 원본 파일을 확인하세요."));errors++;if(errorSamples.size()<8)errorSamples.add(ref+"="+value);}
                if(value.isEmpty()&&!formula){if(style>=0&&style<styles.size()&&pos[0]<=SheetRules.MAX_ROWS&&pos[1]<SheetRules.MAX_COLUMNS)cells.add(new Cell(pos[0],pos[1],"",style));continue;}
                if(pos[0]>SheetRules.MAX_ROWS||pos[1]>=SheetRules.MAX_COLUMNS)throw Problem.invalid(ref+": 데이터는 시트당 100열·50,000행까지 지원합니다.");
                if(value.length()>2000)throw Problem.invalid(ref+": 셀 값은 2,000자 이하입니다.");
                if(style<0||style>=styles.size())throw Problem.invalid(ref+": 셀 서식 참조가 손상되었습니다.");
                if(!Set.of("s","inlineStr","str","b","d","e").contains(type))value=formatted(value,styles.get(style).format(),date1904,ref);
                String nativeType=type.equals("b")?"b":type.equals("e")?"e":Set.of("s","inlineStr","str").contains(type)?"s":type.equals("d")?"d":"n";String nativeRaw=nativeType.equals("s")?value:rawNumeric;
                if(!nativeRaw.equals(value)||nativeType.equals("b")||nativeType.equals("e")||nativeType.equals("s")&&(value.matches("[+-]?[0-9].*")||ExcelCells.error(value))||formula&&!styles.get(style).format().isEmpty())nativeValues.put(ref,List.of(nativeRaw,value,nativeType,styles.get(style).format()));
                cells.add(new Cell(pos[0],pos[1],value,style));height=Math.max(height,pos[0]+1);width=Math.max(width,pos[1]+1);
            }
        }x.close();}catch(XMLStreamException|NumberFormatException e){throw Problem.invalid("시트 ‘"+name+"’의 셀·숫자·XML을 읽지 못했습니다.");}
        for(var follower:sharedFollowers.entrySet()){var master=sharedMasters.get(follower.getValue());if(master==null)throw Problem.invalid(follower.getKey()+": 공유 수식의 원본이 없습니다.");var a=position(master.get(0));var b=position(follower.getKey());try{formulaMap.put(follower.getKey(),copies.translate(master.get(1),name,a[0],a[1],b[0],b[1]));}catch(RuntimeException ex){formulaMap.put(follower.getKey(),translateShared(master.get(1),b[0]-a[0],b[1]-a[1]));}}
        var nonempty=new HashSet<String>();for(var cell:cells)if(!cell.value().isEmpty())nonempty.add(cell.row()+":"+cell.col());for(var m:merges)if(nonempty.contains(m.get(0)+":"+m.get(1))){if(m.get(2)>SheetRules.MAX_ROWS||m.get(3)>=SheetRules.MAX_COLUMNS)throw Problem.invalid("병합 범위가 지원하는 행·열 한도를 초과합니다.");height=Math.max(height,m.get(2)+1);width=Math.max(width,m.get(3)+1);}
        if((long)height*width>SheetRules.MAX_CELLS)throw Problem.invalid("시트의 데이터 범위는 100만 셀 이하로 나누어 주세요.");
        int h=height,w=width;merges.removeIf(m->m.get(0)>=h||m.get(1)>=w||m.get(2)>=h||m.get(3)>=w);
        var matrix=new ArrayList<List<String>>();var styleRows=new ArrayList<List<Integer>>();for(int r=0;r<height;r++){matrix.add(new ArrayList<>(Collections.nCopies(width,"")));styleRows.add(new ArrayList<>(Collections.nCopies(width,0)));}
        for(var c:cells){if(c.row()>=height||c.col()>=width)continue;matrix.get(c.row()).set(c.col(),c.value());styleRows.get(c.row()).set(c.col(),c.style());}
        if(formulas>0)issues.add(issue("FORMULA_PRESERVED","INFO",formulas+"개 원본 수식을 보존했습니다.","계산 가능한 수식은 자동 계산합니다. 검토가 필요한 셀은 주소를 눌러 확인하세요."));
        if(missing>0)issues.add(issue("FORMULA_NO_CACHE","WARNING",missing+"개 수식에 저장된 계산값이 없어 다시 계산했습니다. 원본 위치: "+String.join(", ",missingSamples),"계산하지 못한 셀만 수식 검토 목록에 표시합니다."));
        if(errors>0)issues.add(issue("CELL_ERROR","WARNING",errors+"개 원본 저장 오류가 발견되었습니다. 원본 위치: "+String.join(", ",errorSamples),"재계산 후에도 남은 오류는 수식 검토 목록에서 셀로 이동해 수정하세요."));
        if(drawings>0)issues.add(issue("DRAWING_OBJECTS","WARNING","차트·그림 객체는 가져오지 않습니다.","셀 데이터는 유지합니다. 사진 속 표는 사진으로 만들기를 사용하세요."));
        if(!visibility.equals("visible"))issues.add(issue("HIDDEN_SHEET","WARNING","원본의 숨김 시트입니다.","필요한 경우 시트 목록에서 선택하세요."));
        if(height==0&&drawings>0)return blocked(name,visibility,"DRAWING_WITHOUT_VALUES","그림 객체만 있고 가져올 셀 값이 없습니다.");
        var presentation=new LinkedHashMap<String,Object>();presentation.put("styles",styles.stream().map(Style::visual).toList());presentation.put("cellStyles",styleRows);presentation.put("merges",merges);presentation.put("widths",java.util.stream.IntStream.range(0,width).mapToObj(c->widths.getOrDefault(c,160)).toList());
        var result=matrix(name,matrix,issues,presentation,visibility);var excel=new LinkedHashMap<String,Object>();excel.put("formulas",formulaMap);if(!blockedFormulas.isEmpty())excel.put("blocked",blockedFormulas);excel.put("values",nativeValues);if(!inputIssues.isEmpty())excel.put("issues",inputIssues);if(date1904)excel.put("date1904",true);result.put("excel",excel);return result;
    }
    private static Map<String,Object> matrix(String name,List<List<String>> matrix,List<Map<String,Object>> issues,Map<String,Object> presentation,String visibility){
        if(presentation.isEmpty())while(!matrix.isEmpty()&&matrix.get(matrix.size()-1).stream().allMatch(String::isEmpty))matrix.remove(matrix.size()-1);
        int width=matrix.stream().mapToInt(List::size).max().orElse(0);if(width>SheetRules.MAX_COLUMNS||matrix.size()>SheetRules.MAX_ROWS+1||(long)width*matrix.size()>SheetRules.MAX_CELLS)throw Problem.invalid("시트는 최대 100열·50,000행·100만 셀입니다.");
        for(var row:matrix){while(row.size()<width)row.add("");for(String v:row)if(v.length()>2000)throw Problem.invalid("셀 값은 2,000자 이하입니다.");}
        var result=new LinkedHashMap<String,Object>();result.put("sheetName",name);result.put("visibility",visibility);result.put("status",matrix.isEmpty()?"EMPTY":"READY");result.put("headers",matrix.isEmpty()?List.of():matrix.get(0));result.put("rows",matrix.isEmpty()?List.of():matrix.subList(1,matrix.size()));result.put("issues",issues);result.put("warnings",List.of());result.put("presentation",presentation);
        return result;
    }
    static String formatted(String value,String format,boolean date1904,String ref){
        String simplified=format.replaceAll("\"[^\"]*\"|\\[[^]]*]","").toLowerCase(Locale.ROOT);
        try{
            if(simplified.contains("y")||simplified.contains("d")){double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<0||n>2958465)throw Problem.invalid(ref+": 날짜 일련번호 범위를 확인하세요.");long day=(long)Math.floor(n);if(!date1904&&day==60)return "1900-02-29";var date=date1904?LocalDate.of(1904,1,1).plusDays(day):LocalDate.of(1899,12,31).plusDays(day-(day>=60?1:0));return date+(n!=day?"T"+LocalTime.ofSecondOfDay(Math.min(86399,Math.round((n-day)*86400))):"");}
            if(simplified.contains("h")||simplified.contains("s")){double n=Double.parseDouble(value);long seconds=Math.round((n-Math.floor(n))*86400);return LocalTime.ofSecondOfDay(Math.min(86399,seconds)).toString();}
            if(format.matches("0{2,15}")&&value.matches("[0-9]+"))return "0".repeat(Math.max(0,format.length()-value.length()))+value;
            if(simplified.contains("%"))return new BigDecimal(value).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%";
            return value;
        }catch(NumberFormatException|DateTimeException e){throw Problem.invalid(ref+": 숫자·날짜 서식을 확인하세요.");}
    }
    private static List<Style> styles(byte[] data){var out=new ArrayList<Style>();if(data==null)return List.of(new Style("",Map.of()));var doc=xml(data);var formats=new HashMap<String,String>();var fs=doc.getElementsByTagNameNS("*","numFmt");for(int i=0;i<fs.getLength();i++){var f=(Element)fs.item(i);formats.put(f.getAttribute("numFmtId"),f.getAttribute("formatCode"));}var xfs=doc.getElementsByTagNameNS("*","cellXfs");if(xfs.getLength()==0)return List.of(new Style("",Map.of()));var nodes=((Element)xfs.item(0)).getElementsByTagNameNS("*","xf");for(int i=0;i<nodes.getLength();i++){var xf=(Element)nodes.item(i);var visual=new LinkedHashMap<String,Object>();Element font=indexed(doc,"fonts","font",xf.getAttribute("fontId")),fill=indexed(doc,"fills","fill",xf.getAttribute("fillId"));if(font!=null){if(font.getElementsByTagNameNS("*","b").getLength()>0)visual.put("bold",true);String color=color(font,"color");if(!color.isEmpty())visual.put("color",color);}if(fill!=null){String color=color(fill,"fgColor");if(!color.isEmpty())visual.put("background",color);}var align=xf.getElementsByTagNameNS("*","alignment");if(align.getLength()>0){String a=((Element)align.item(0)).getAttribute("horizontal");if(Set.of("left","right","center").contains(a))visual.put("align",a);}String id=xf.getAttribute("numFmtId");out.add(new Style(formats.getOrDefault(id,Set.of("14","15","16","17","22").contains(id)?"yyyy-mm-dd":Set.of("18","19","20","21").contains(id)?"hh:mm:ss":Set.of("9","10").contains(id)?"0%":""),visual));}return out.isEmpty()?List.of(new Style("",Map.of())):out;}
    private static Element indexed(Document d,String container,String item,String id){try{var lists=d.getElementsByTagNameNS("*",container);return lists.getLength()==0?null:(Element)((Element)lists.item(0)).getElementsByTagNameNS("*",item).item(Integer.parseInt(id));}catch(RuntimeException e){return null;}}
    private static String color(Element e,String tag){var ns=e.getElementsByTagNameNS("*",tag);if(ns.getLength()==0)return "";String rgb=((Element)ns.item(0)).getAttribute("rgb");return rgb.matches("[A-Fa-f0-9]{8}")?"#"+rgb.substring(2):rgb.matches("[A-Fa-f0-9]{6}")?"#"+rgb:"";}
    private static Map<String,Object> issue(String c,String s,String m,String r){return Map.of("code",c,"severity",s,"message",m,"remedy",r);}
    static List<List<String>> csv(String input){var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var field=new StringBuilder();boolean quoted=false,after=false;for(int i=0;i<input.length();i++){char c=input.charAt(i);if(quoted){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;after=true;}}else field.append(c);}else if(c=='"'){if(field.length()>0||after)throw Problem.invalid("CSV 따옴표를 확인하세요.");quoted=true;}else if(c==','||c=='\r'||c=='\n'){row.add(field.toString());field.setLength(0);after=false;if(c!=','){rows.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;}}else{if(after)throw Problem.invalid("CSV 닫는 따옴표 뒤 문자를 확인하세요.");field.append(c);}if(field.length()>2000||row.size()>SheetRules.MAX_COLUMNS||rows.size()>SheetRules.MAX_ROWS+1)throw Problem.invalid("CSV 크기가 지원 범위를 초과합니다.");}if(quoted)throw Problem.invalid("CSV 따옴표가 닫히지 않았습니다.");if(field.length()>0||!row.isEmpty()||after){row.add(field.toString());rows.add(row);}return rows;}
    private static Map<String,byte[]> unzip(byte[] bytes){var parts=new HashMap<String,byte[]>();var seen=new HashSet<String>();long total=0;try(var z=new ZipInputStream(new ByteArrayInputStream(bytes))){int count=0;for(ZipEntry e;(e=z.getNextEntry())!=null;){String name=e.getName();if(++count>1000||name.contains("..")||name.startsWith("/")||name.contains("\\")||!seen.add(name))throw Problem.invalid("엑셀 압축 구조를 확인하세요.");if(e.isDirectory())continue;var out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n,part=0;boolean keep=name.equals("xl/workbook.xml")||name.equals("xl/_rels/workbook.xml.rels")||name.equals("xl/sharedStrings.xml")||name.equals("xl/styles.xml")||name.startsWith("xl/worksheets/")&&name.endsWith(".xml");while((n=z.read(buf))!=-1){part+=n;total+=n;if(part>PART||total>EXPANDED)throw Problem.invalid("엑셀 압축 해제 한도(전체 128MB·개별 구성요소 64MB)를 초과합니다.");if(keep)out.write(buf,0,n);}if(keep)parts.put(name,out.toByteArray());}}catch(IOException e){throw Problem.invalid("암호화되었거나 손상된 XLSX입니다. 일반 XLSX로 다시 저장하세요.");}return parts;}
    private static byte[] required(Map<String,byte[]> p,String key){byte[] b=p.get(key);if(b==null)throw Problem.invalid("손상되었거나 암호화된 XLSX입니다. 필수 구성요소가 없습니다: "+key);return b;}
    private static Document xml(byte[] b){try{var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));}catch(Exception e){throw Problem.invalid("안전하게 읽을 수 없는 엑셀 XML입니다.");}}
}
