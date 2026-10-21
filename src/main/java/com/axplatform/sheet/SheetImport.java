package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.io.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Read-only preview. Workbook expressions/macros/external links are never executed. */
public final class SheetImport {
    private SheetImport() { }
    public static Map<String,Object> read(byte[] bytes,String filename,String encoding,String selected) {
        return readSingle(bytes,filename,encoding,selected,null);
    }
    private static Map<String,Object> readSingle(byte[] bytes,String filename,String encoding,String selected,Map<String,byte[]> cachedParts) {
        if(bytes.length==0||bytes.length>10*1024*1024)throw Problem.invalid("파일은 0바이트 초과, 10MB 이내로 선택하세요.");
        List<String> warnings=new ArrayList<>();List<List<String>> matrix;List<String> names=new ArrayList<>();String sheetName="CSV";
        if(filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            if(!Set.of("UTF-8","MS949").contains(encoding))throw Problem.invalid("CSV 문자 인코딩을 선택하세요.");
            try {String text=Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();matrix=csv(text.replaceFirst("^\\uFEFF",""));}
            catch(CharacterCodingException e){throw Problem.invalid("문자 인코딩이 맞지 않습니다. UTF-8 또는 한글 Windows(CP949)를 선택하세요.");}
        } else if(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            Map<String,byte[]> parts=cachedParts==null?unzip(bytes):cachedParts;Document workbook=xml(required(parts,"xl/workbook.xml"));Document relationships=xml(required(parts,"xl/_rels/workbook.xml.rels"));
            NodeList sheetNodes=workbook.getElementsByTagNameNS("*","sheet");if(sheetNodes.getLength()==0)throw Problem.invalid("엑셀 워크시트가 없습니다.");
            Element chosen=null;for(int i=0;i<sheetNodes.getLength();i++){Element s=(Element)sheetNodes.item(i);names.add(s.getAttribute("name"));if((selected==null||selected.isBlank())&&i==0||s.getAttribute("name").equals(selected))chosen=s;}
            if(chosen==null)throw Problem.invalid("선택한 워크시트가 없습니다.");sheetName=chosen.getAttribute("name");String rid=chosen.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id"),path=null;
            NodeList rels=relationships.getElementsByTagNameNS("*","Relationship");for(int i=0;i<rels.getLength();i++){Element rel=(Element)rels.item(i);if(rid.equals(rel.getAttribute("Id"))){if("External".equals(rel.getAttribute("TargetMode")))throw Problem.invalid("외부 연결 시트는 사용할 수 없습니다.");String t=rel.getAttribute("Target");path=t.startsWith("/")?t.substring(1):"xl/"+t;}}
            if(path==null||path.contains(".."))throw Problem.invalid("워크시트 경로가 올바르지 않습니다.");
            List<String> shared=new ArrayList<>();if(parts.containsKey("xl/sharedStrings.xml")){NodeList si=xml(parts.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*","si");for(int i=0;i<si.getLength();i++)shared.add(texts((Element)si.item(i),"t"));}
            Document data=xml(required(parts,path));if(data.getElementsByTagNameNS("*","mergeCell").getLength()>0)throw Problem.invalid("병합 셀 "+attributes(data,"mergeCell","ref")+"이 있습니다. Excel에서 병합을 해제하고 각 행의 값을 채운 뒤 다시 올려 주세요.");
            List<String> formats=new ArrayList<>();boolean date1904=false;NodeList props=workbook.getElementsByTagNameNS("*","workbookPr");if(props.getLength()>0)date1904=Set.of("1","true").contains(((Element)props.item(0)).getAttribute("date1904"));
            if(parts.containsKey("xl/styles.xml")){Document styles=xml(parts.get("xl/styles.xml"));Map<String,String> custom=new HashMap<>();NodeList nf=styles.getElementsByTagNameNS("*","numFmt");for(int i=0;i<nf.getLength();i++){Element n=(Element)nf.item(i);custom.put(n.getAttribute("numFmtId"),n.getAttribute("formatCode"));}NodeList cx=styles.getElementsByTagNameNS("*","cellXfs");if(cx.getLength()>0){NodeList xfs=((Element)cx.item(0)).getElementsByTagNameNS("*","xf");for(int i=0;i<xfs.getLength();i++){String n=((Element)xfs.item(i)).getAttribute("numFmtId");formats.add(custom.getOrDefault(n,Set.of("14","15","16","17","22").contains(n)?"yyyy-mm-dd":""));}}}
            TreeMap<Integer,TreeMap<Integer,String>> cells=new TreeMap<>();int width=0;boolean hasFormula=false,hasPercent=false;
            NodeList nodes=data.getElementsByTagNameNS("*","c");if(nodes.getLength()>10000)throw Problem.invalid("시트가 너무 큽니다. 20열·200행 이내로 나누세요.");
            for(int i=0;i<nodes.getLength();i++){Element c=(Element)nodes.item(i);String reference=c.getAttribute("r");if(!reference.matches("[A-Z]{1,3}[1-9][0-9]{0,6}"))throw Problem.invalid("엑셀 셀 위치를 읽지 못했습니다.");String letters=reference.replaceAll("[0-9]","");int col=0;for(char ch:letters.toCharArray())col=col*26+ch-'A'+1;int row=Integer.parseInt(reference.substring(letters.length()));String type=c.getAttribute("t"),v=texts(c,"v");boolean formula=c.getElementsByTagNameNS("*","f").getLength()>0;hasFormula|=formula;
                if(formula&&v.isEmpty()&&!"str".equals(type))throw Problem.invalid(reference+" 수식의 저장된 계산값이 없습니다. Excel에서 계산 후 저장하거나 값으로 붙여넣어 업로드하세요.");
                if("s".equals(type)){try{v=shared.get(Integer.parseInt(v));}catch(RuntimeException e){throw Problem.invalid("문자 셀 참조가 올바르지 않습니다.");}}
                else if("inlineStr".equals(type))v=texts(c,"t");else if("e".equals(type))throw Problem.invalid(reference+"에 Excel 오류 값이 있습니다: "+v);else if("b".equals(type))v="1".equals(v)?"TRUE":"FALSE";
                if(v.isEmpty())continue;
                if(col>20||row>201)throw Problem.invalid("선택 시트는 제목 1행 외 최대 20열·200행입니다. 범위를 나누어 주세요.");if(v.length()>2000)throw Problem.invalid(reference+"은 2,000자를 초과합니다.");
                if(!Set.of("s","inlineStr","str","b","d").contains(type)&&!c.getAttribute("s").isEmpty()){
                    try{int style=Integer.parseInt(c.getAttribute("s"));if(style<0||style>=formats.size())throw Problem.invalid(reference+"의 셀 서식 참조가 손상되었습니다. Excel에서 새 XLSX로 저장해 주세요.");String f=formats.get(style);String simplified=f.replaceAll("\"[^\"]*\"|\\[[^]]*]","").toLowerCase(Locale.ROOT);
                        if(simplified.contains("y")||simplified.contains("d")){double serial=Double.parseDouble(v);if(!Double.isFinite(serial)||serial<0||serial>2958465)throw Problem.invalid(reference+"의 날짜 일련번호가 지원 범위를 벗어났습니다. 원본 날짜를 확인하거나 문자 값으로 저장해 주세요.");long day=(long)Math.floor(serial);if(!date1904&&day==60)throw Problem.invalid(reference+"은 Excel의 1900-02-29 날짜 오류입니다.");LocalDate date=date1904?LocalDate.of(1904,1,1).plusDays(day):LocalDate.of(1899,12,31).plusDays(day-(day>=60?1:0));v=date.toString();if(serial!=day){long seconds=Math.round((serial-day)*86400);v+="T"+java.time.LocalTime.ofSecondOfDay(Math.min(86399,seconds));warnings.add("시간이 포함된 날짜는 문자로 읽습니다. 날짜만 필요하면 편집에서 확인하세요.");}}
                        else if(f.matches("0{2,15}")&&v.matches("[0-9]+"))v="0".repeat(Math.max(0,f.length()-v.length()))+v;
                        else if(f.contains("%"))hasPercent=true;
                    }catch(NumberFormatException|DateTimeException e){throw Problem.invalid(reference+"의 숫자·날짜·셀 서식을 읽지 못했습니다. 해당 셀을 수정하고 새 XLSX로 저장해 주세요.");}
                }
                cells.computeIfAbsent(row,k->new TreeMap<>()).put(col,v);width=Math.max(width,col);
            }
            if(cells.isEmpty())return Map.of("sheetNames",names,"sheetName",sheetName,"headers",List.of(),"rows",List.of(),"warnings",List.of("셀 값이 없는 빈 시트입니다. 빈 시트로 추가할 수 있습니다."));matrix=new ArrayList<>();for(int r=1;r<=cells.lastKey();r++){List<String> line=new ArrayList<>();for(int c=1;c<=width;c++)line.add(cells.getOrDefault(r,new TreeMap<>()).getOrDefault(c,""));matrix.add(line);}
            if(hasFormula)warnings.add("원본 수식은 실행·복사하지 않고 파일에 저장된 계산값만 가져옵니다. 필요한 계산 열은 편집 화면에서 다시 설정하세요.");
            if(hasPercent)warnings.add("백분율은 실제 저장값으로 읽습니다. 예: 10% → 0.1. 금액·백분율·날짜 단위와 열 형식을 직접 확인하세요.");
            warnings.add("셀 값만 가져옵니다. 원본 수식·서식·차트·이미지·매크로 실행은 복원하지 않습니다.");
        } else throw Problem.invalid("XLSX 또는 CSV 파일을 선택하세요. XLS·XLSM·PDF는 현재 지원하지 않습니다.");
        while(!matrix.isEmpty()&&matrix.get(matrix.size()-1).stream().allMatch(String::isEmpty))matrix.remove(matrix.size()-1);
        if(matrix.isEmpty())throw Problem.invalid("제목 행이 없습니다.");int width=matrix.stream().mapToInt(List::size).max().orElse(0);
        if(width<1||width>20||matrix.size()>201)throw Problem.invalid("제목 1행 외 최대 20열·200행입니다.");
        for(List<String> row:matrix){while(row.size()<width)row.add("");if(row.stream().anyMatch(v->v.length()>2000))throw Problem.invalid("한 셀은 2,000자 이내입니다.");}
        if(matrix.get(0).stream().anyMatch(h->h.trim().length()>100))throw Problem.invalid("첫 행의 열 제목은 100자 이내여야 합니다. 긴 제목을 줄이거나 설명을 데이터 행으로 옮긴 후 다시 올려 주세요.");
        return Map.of("sheetNames",names,"sheetName",sheetName,"headers",matrix.get(0),"rows",matrix.subList(1,matrix.size()),"warnings",new ArrayList<>(new LinkedHashSet<>(warnings)));
    }
    public static Map<String,Object> readWorkbook(byte[] bytes,String filename,String encoding) {
        if(bytes.length==0 || bytes.length>10*1024*1024) throw Problem.invalid("파일은 0바이트 초과, 10MB 이내여야 합니다.");
        String lower=filename.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".xlsx")&&!lower.endsWith(".csv"))throw Problem.invalid("XLSX 또는 CSV를 선택하세요. XLS·XLSM은 Excel에서 매크로 없는 XLSX로 다른 이름 저장 후 올려 주세요. PDF와 사진은 사진 가져오기를 사용하세요.");
        List<Map<String,Object>> results=new ArrayList<>();
        if(lower.endsWith(".csv")) {
            var r=new LinkedHashMap<>(read(bytes,filename,encoding,""));r.put("status","READY");r.put("issues",List.of());r.put("visibility","visible");results.add(r);
        } else {
            if(bytes.length>=2 && (bytes[0]!='P'||bytes[1]!='K'))throw Problem.invalid("일반 XLSX 압축 형식이 아닙니다. 암호가 걸린 파일은 Excel에서 암호를 해제하고, 구형 XLS 파일은 XLSX로 다시 저장하세요.");
            Map<String,byte[]> parts=unzip(bytes);Document wb=xml(required(parts,"xl/workbook.xml"));
            NodeList entries=wb.getElementsByTagNameNS("*","sheet");
            if(entries.getLength()==0)throw Problem.invalid("가져올 시트가 없습니다.");
            if(entries.getLength()>WorkbookRules.MAX_SHEETS)throw Problem.invalid("이 파일에는 "+entries.getLength()+"개 시트가 있습니다. 프로젝트는 최대 20시트이므로 Excel에서 파일을 나누어 올려 주세요.");
            Document relations=xml(required(parts,"xl/_rels/workbook.xml.rels"));
            for(int i=0;i<entries.getLength();i++) {
                Element sheet=(Element)entries.item(i);String title=sheet.getAttribute("name");List<Map<String,Object>> issues=new ArrayList<>();
                Map<String,Object> result=new LinkedHashMap<>();result.put("sheetName",title);result.put("visibility",sheet.hasAttribute("state")?sheet.getAttribute("state"):"visible");
                result.put("headers",List.of());result.put("rows",List.of());result.put("warnings",List.of());
                try {
                    String rid=sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");Element relation=null;
                    NodeList links=relations.getElementsByTagNameNS("*","Relationship");for(int j=0;j<links.getLength();j++){Element l=(Element)links.item(j);if(rid.equals(l.getAttribute("Id")))relation=l;}
                    if(relation==null)throw Problem.invalid("시트 연결 정보를 찾을 수 없습니다.");
                    if(!relation.getAttribute("Type").endsWith("/worksheet")) {
                        result.put("status","BLOCKED");boolean chart=relation.getAttribute("Type").endsWith("/chartsheet");issues.add(issue(chart?"CHART_SHEET":"UNSUPPORTED_SHEET","ERROR",chart?"차트 전용 시트는 셀 표가 아니므로 가져올 수 없습니다.":"일반 워크시트가 아닌 시트 형식입니다. 매크로·대화상자 등의 시트는 가져오지 않습니다.","필요한 원본 데이터를 일반 워크시트로 복사해 XLSX로 저장해 주세요."));
                    } else {
                        String target=relation.getAttribute("Target");String path=target.startsWith("/")?target.substring(1):"xl/"+target;
                        if("External".equals(relation.getAttribute("TargetMode"))||path.contains(".."))throw Problem.invalid("외부 연결 또는 잘못된 시트 경로입니다.");
                        Document data=xml(required(parts,path));List<String> formulas=new ArrayList<>(),missing=new ArrayList<>(),errors=new ArrayList<>();
                        NodeList cells=data.getElementsByTagNameNS("*","c");
                        for(int j=0;j<cells.getLength();j++){Element c=(Element)cells.item(j);if(c.getElementsByTagNameNS("*","f").getLength()>0){formulas.add(c.getAttribute("r"));if(c.getElementsByTagNameNS("*","v").getLength()==0 || (texts(c,"v").isEmpty()&&!"str".equals(c.getAttribute("t"))))missing.add(c.getAttribute("r"));}if("e".equals(c.getAttribute("t")))errors.add(c.getAttribute("r")+"="+texts(c,"v"));}
                        if(!formulas.isEmpty())issues.add(issue("FORMULA_VALUES","WARNING",formulas.size()+"개 수식 셀("+sample(formulas)+")은 수식 자체가 아닌 파일에 저장된 계산값만 가져옵니다.","값을 최신 상태로 보관하려면 Excel에서 전체 다시 계산 후 저장하거나, 복사 → 선택하여 붙여넣기 → 값을 사용하세요. 시트 간 수식도 값으로만 들어옵니다."));
                        if(!missing.isEmpty())issues.add(issue("FORMULA_NO_CACHE","ERROR","계산 결과가 저장되지 않은 수식 셀: "+sample(missing),"Excel에서 수식을 다시 계산하고 저장하거나 값으로 붙여넣은 파일을 올려 주세요. 빈 값으로 임의 처리하지 않습니다."));
                        if(!errors.isEmpty())issues.add(issue("CELL_ERROR","ERROR","Excel 오류 셀: "+sample(errors),"원본 파일에서 #REF!·#DIV/0! 등의 원인을 수정한 뒤 다시 올려 주세요."));
                        if(data.getElementsByTagNameNS("*","mergeCell").getLength()>0)issues.add(issue("MERGED_CELLS","ERROR","병합 범위: "+attributes(data,"mergeCell","ref"),"병합을 해제하고 제목은 첫 행에, 각 행의 실제 값은 개별 셀에 입력하세요."));
                        int drawings=data.getElementsByTagNameNS("*","drawing").getLength()+data.getElementsByTagNameNS("*","legacyDrawing").getLength();
                        if(drawings>0)issues.add(issue("DRAWING_OBJECTS","WARNING","차트·이미지·도형 등의 그리기 객체가 포함되어 있습니다. 해당 객체는 가져오지 않으며 셀 데이터만 읽습니다.","그래프의 수치를 사용하려면 원본 데이터 표를 올리세요. 사진 속 표는 사진 가져오기를 선택하세요."));
                        if(parts.keySet().stream().anyMatch(k->k.startsWith("xl/externalLinks/")))issues.add(issue("EXTERNAL_LINKS","WARNING","통합문서에 외부 파일 연결이 있습니다. 연결된 파일을 열거나 갱신하지 않습니다.","원본 Excel에서 연결값을 갱신하고 값으로 저장한 파일을 사용하세요."));
                        if(!"visible".equals(result.get("visibility")))issues.add(issue("HIDDEN_SHEET","WARNING","원본에서 숨겨진 시트입니다. 기본 선택에서 제외했습니다.","내용을 확인한 뒤 직접 선택한 경우에만 일반 시트로 가져옵니다."));
                        if(issues.stream().anyMatch(v->"ERROR".equals(v.get("severity"))))result.put("status","BLOCKED");
                        else {result.putAll(readSingle(bytes,filename,encoding,title,parts));boolean empty=((List<?>)result.get("headers")).isEmpty();result.put("status",empty?"EMPTY":"READY");if(empty&&drawings>0){result.put("status","BLOCKED");issues.add(issue("DRAWING_WITHOUT_VALUES","ERROR","그리기 객체는 있지만 가져올 셀 값이 없습니다.","차트의 원본 데이터 표를 별도 워크시트로 저장하거나, 표 사진을 PNG/JPG로 저장해 사진 가져오기를 사용하세요."));}}
                    }
                } catch(Problem error) {result.put("status","BLOCKED");issues.add(issue("SHEET_READ_FAILED","ERROR",error.getMessage(),"안내된 시트·셀·범위를 수정한 뒤 이 파일을 다시 읽으세요. 다른 정상 시트는 선택해 가져올 수 있습니다."));}
                result.put("issues",issues);results.add(result);
            }
        }
        return Map.of("fileName",filename,"sheets",results,"maxSheets",WorkbookRules.MAX_SHEETS,"maxColumns",SheetRules.MAX_COLUMNS,"maxRows",SheetRules.MAX_ROWS);
    }
    private static Map<String,Object> issue(String code,String severity,String message,String remedy){return Map.of("code",code,"severity",severity,"message",message,"remedy",remedy);}
    private static String sample(List<String> values){return String.join(", ",values.subList(0,Math.min(12,values.size())))+(values.size()>12?" 외 "+(values.size()-12)+"개":"");}
    private static String attributes(Document doc,String tag,String attr){NodeList ns=doc.getElementsByTagNameNS("*",tag);List<String> values=new ArrayList<>();for(int i=0;i<Math.min(12,ns.getLength());i++)values.add(((Element)ns.item(i)).getAttribute(attr));return String.join(", ",values)+(ns.getLength()>12?" 외 "+(ns.getLength()-12)+"개":"");}
    static List<List<String>> csv(String input){List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quote=false,closed=false;
        for(int i=0;i<input.length();i++){char c=input.charAt(i);if(quote){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){cell.append('"');i++;}else{quote=false;closed=true;}}else cell.append(c);}
            else if(c=='"'){if(cell.length()>0||closed)throw Problem.invalid("CSV 따옴표 형식이 잘못되었습니다.");quote=true;}
            else if(c==','||c=='\r'||c=='\n'){row.add(cell.toString());cell.setLength(0);closed=false;if(row.size()>20)throw Problem.invalid("CSV는 최대 20열입니다.");if(c!=','){rows.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;if(rows.size()>201)throw Problem.invalid("CSV는 제목 외 200행 이내입니다.");}}
            else{if(closed)throw Problem.invalid("CSV 닫는 따옴표 다음은 쉼표 또는 줄바꿈이어야 합니다.");cell.append(c);}if(cell.length()>2000)throw Problem.invalid("CSV 한 셀은 2,000자 이내입니다.");}
        if(quote)throw Problem.invalid("CSV 따옴표가 닫히지 않았습니다.");if(cell.length()>0||!row.isEmpty()||closed){row.add(cell.toString());rows.add(row);}return rows;}
    private static Map<String,byte[]> unzip(byte[] bytes){Map<String,byte[]> parts=new HashMap<>();long total=0;try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){int count=0;for(ZipEntry e;(e=z.getNextEntry())!=null;){if(++count>500)throw Problem.invalid("엑셀 내부 파일이 너무 많습니다.");if(e.isDirectory())continue;if(e.getName().contains("..")||parts.containsKey(e.getName()))throw Problem.invalid("잘못된 압축 경로입니다.");byte[] b=z.readNBytes(20*1024*1024+1);total+=b.length;if(total>20*1024*1024)throw Problem.invalid("압축 해제 크기는 20MB 이내입니다.");parts.put(e.getName(),b);}}catch(IOException e){throw Problem.invalid("XLSX 압축을 읽지 못했습니다.");}return parts;}
    private static byte[] required(Map<String,byte[]> p,String key){byte[] b=p.get(key);if(b==null)throw Problem.invalid("엑셀 필수 구성요소가 없습니다: "+key);return b;}
    private static Document xml(byte[] b){try{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));}catch(Exception e){throw Problem.invalid("안전하게 읽을 수 없는 엑셀 XML입니다.");}}
    private static String texts(Element e,String tag){StringBuilder s=new StringBuilder();NodeList n=e.getElementsByTagNameNS("*",tag);for(int i=0;i<n.getLength();i++)s.append(n.item(i).getTextContent());return s.toString();}
}
