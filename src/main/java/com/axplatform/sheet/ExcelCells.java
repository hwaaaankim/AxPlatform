package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.util.*;
import java.util.regex.*;

/** Sparse A1 metadata. Row 1 is the original header; data rows start at row 2. */
public final class ExcelCells {
 private ExcelCells(){}
 public static final int MAX_FORMULAS=200_000;
 public static String ref(int row,int col){return SheetFormula.letter(col)+(row+1);}
 public static int[] position(String ref){if(ref==null||!ref.matches("[A-Z]{1,2}[1-9][0-9]{0,4}"))throw Problem.invalid("셀 주소를 확인하세요: "+ref);int split=0,c=0;while(split<ref.length()&&Character.isLetter(ref.charAt(split)))c=c*26+ref.charAt(split++)-'A'+1;return new int[]{Integer.parseInt(ref.substring(split))-1,c-1};}
 @SuppressWarnings("unchecked") public static Map<String,Object> meta(Map<String,Object> data){return data.get("excel") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
 @SuppressWarnings("unchecked") public static <T> Map<String,T> map(Map<String,Object> meta,String key){return meta.get(key) instanceof Map<?,?> m?(Map<String,T>)m:Map.of();}
 public static Map<String,Object> normalize(Object raw,int rows,int cols){
  if(!(raw instanceof Map<?,?> m)||!Set.of("formulas","values","paused","issues","blocked","date1904").containsAll(m.keySet()))throw Problem.invalid("수식 메타데이터를 확인하세요.");
  var out=new LinkedHashMap<String,Object>();long chars=0;
  for(String key:List.of("formulas","values","paused","issues","blocked")){
   Object input=m.containsKey(key)?m.get(key):Map.of();if(!(input instanceof Map<?,?> entries)||entries.size()>MAX_FORMULAS)throw Problem.invalid("수식·형식 셀은 시트당 20만 개 이하입니다.");
   var clean=new LinkedHashMap<String,Object>();
   for(var e:entries.entrySet()){
    if(!(e.getKey() instanceof String address))throw Problem.invalid("수식 셀 주소를 확인하세요.");var at=position(address);if(at[0]>rows||at[1]>=cols)throw Problem.invalid(address+": 표 범위 밖 수식·형식입니다.");Object value=e.getValue();
    if(key.equals("formulas")){if(!(value instanceof String f)||f.isBlank()||f.length()>8192||f.indexOf('\0')>=0)throw Problem.invalid(address+": 수식은 1~8,192자입니다.");String formula=((String)value).strip();value=formula.startsWith("=")?formula.substring(1):formula;chars+=formula.length();}
    else if(key.equals("blocked")){if(!(value instanceof String reason)||reason.length()>2000)throw Problem.invalid(address+": 수식 제한 사유를 확인하세요.");}
    else if(key.equals("values")){if(!(value instanceof List<?> v)||v.size()!=4||v.stream().anyMatch(x->!(x instanceof String s)||s.length()>2000)||!Set.of("n","s","b","e","d").contains(v.get(2)))throw Problem.invalid(address+": 원본 셀 형식을 확인하세요.");value=List.copyOf(v);}
    else {if(!(value instanceof Map<?,?> v)||!Set.of("formula","cached","code","message","mode").containsAll(v.keySet())||v.values().stream().anyMatch(x->!(x instanceof String s)||s.length()>8192))throw Problem.invalid(address+": 수식 검토 정보를 확인하세요.");value=new LinkedHashMap<>(v);}
    clean.put(address,value);
   }
   if(!clean.isEmpty())out.put(key,clean);
  }
  if(chars>8_000_000)throw Problem.invalid("시트 수식 문자열은 8MB 이하로 나누세요.");
  if(m.containsKey("date1904")){if(!(m.get("date1904") instanceof Boolean))throw Problem.invalid("Excel 날짜 체계를 확인하세요.");if(Boolean.TRUE.equals(m.get("date1904")))out.put("date1904",true);}
  return out;
 }
 @SuppressWarnings("unchecked") public static String value(Map<String,Object> data,String address){var at=position(address);if(at[0]==0){var c=((List<Map<String,Object>>)data.get("columns")).get(at[1]);return Objects.toString(c.getOrDefault("sourceHeader",c.get("label")),"");}return ((List<List<String>>)data.get("rows")).get(at[0]-1).get(at[1]);}
 @SuppressWarnings("unchecked") public static void put(Map<String,Object> data,String address,String value){var at=position(address);if(at[0]==0)((List<Map<String,Object>>)data.get("columns")).get(at[1]).put("sourceHeader",value);else ((List<List<String>>)data.get("rows")).get(at[0]-1).set(at[1],value);}
 @SuppressWarnings("unchecked") public static Map<String,Object> mutable(Map<String,Object> data){var out=new LinkedHashMap<>(data);out.put("columns",new ArrayList<>(((List<Map<String,Object>>)data.get("columns")).stream().map(LinkedHashMap::new).toList()));out.put("rows",new ArrayList<>(((List<List<String>>)data.get("rows")).stream().map(ArrayList::new).toList()));var meta=new LinkedHashMap<>(meta(data));for(String key:List.of("formulas","values","paused","issues","blocked"))meta.put(key,new LinkedHashMap<>(map(meta,key)));out.put("excel",meta);return out;}
 public static boolean hasFormula(Map<String,Object> data,String ref){return map(meta(data),"formulas").containsKey(ref);}
 public static boolean hasAny(Map<String,Object> data){return !map(meta(data),"formulas").isEmpty()||!map(meta(data),"paused").isEmpty();}
 public static boolean error(String v){return Set.of("#DIV/0!","#VALUE!","#REF!","#NAME?","#NUM!","#N/A","#NULL!","#CIRC!","#SPILL!","#CALC!","~CIRCULAR~REF~","~FUNCTION~NOT~IMPLEMENTED~").contains(v);}
 /** AI has an explicit formula channel: a row value beginning with '=' is a reviewed formula. */
 @SuppressWarnings("unchecked") public static Map<String,Object> aiFormulas(Map<String,Object> raw,int offset){var out=mutable(raw);var formulas=ExcelCells.<String>map(meta(out),"formulas");var rows=(List<List<String>>)out.get("rows");for(int r=0;r<rows.size();r++)for(int c=0;c<rows.get(r).size();c++){String s=rows.get(r).get(c);if(s.startsWith("=")){formulas.put(ref(r+1,c),s.substring(1));rows.get(r).set(c,"");}}return out;}
}
