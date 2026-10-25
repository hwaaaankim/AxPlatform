package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.util.*;

/** Closed visual vocabulary: user JSON never becomes arbitrary CSS/HTML. Coordinates include header row. */
public final class SheetPresentation {
    private SheetPresentation() { }
    private static int integer(Object o,int max){if(!(o instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<0||n.intValue()>max)throw Problem.invalid("표 표시 범위를 확인하세요.");return n.intValue();}
    public static Map<String,Object> normalize(Object raw,int rows,int columns){
        if(!(raw instanceof Map<?,?> p))throw Problem.invalid("표 서식을 확인하세요.");
        var styles=new ArrayList<Map<String,Object>>();
        if(p.get("styles") instanceof List<?> ss){if(ss.size()>10000)throw Problem.invalid("셀 서식 수를 줄여 주세요.");for(Object item:ss){if(!(item instanceof Map<?,?> s))throw Problem.invalid("셀 서식을 확인하세요.");var clean=new LinkedHashMap<String,Object>();for(String key:List.of("color","background"))if(s.containsKey(key)){String color=Objects.toString(s.get(key));if(!color.matches("#[0-9a-fA-F]{6}"))throw Problem.invalid("셀 색상을 확인하세요.");clean.put(key,color);}if(Boolean.TRUE.equals(s.get("bold")))clean.put("bold",true);if(s.containsKey("align")){String a=Objects.toString(s.get("align"));if(!Set.of("left","right","center").contains(a))throw Problem.invalid("셀 정렬을 확인하세요.");clean.put("align",a);}styles.add(clean);}}
        if(styles.isEmpty())styles.add(Map.of());
        var cellStyles=new ArrayList<List<Integer>>();if(p.get("cellStyles") instanceof List<?> rs){if(rs.size()>rows+1)throw Problem.invalid("행 서식 범위를 확인하세요.");for(Object item:rs){if(!(item instanceof List<?> r)||r.size()>columns)throw Problem.invalid("열 서식 범위를 확인하세요.");cellStyles.add(r.stream().map(v->integer(v,styles.size()-1)).toList());}}
        var merges=new ArrayList<List<Integer>>();var occupied=new BitSet();if(p.get("merges") instanceof List<?> ms){if(ms.size()>100000)throw Problem.invalid("병합 범위가 너무 많습니다.");for(Object item:ms){if(!(item instanceof List<?> m)||m.size()!=4)throw Problem.invalid("병합 범위를 확인하세요.");int r1=integer(m.get(0),rows),c1=integer(m.get(1),Math.max(0,columns-1)),r2=integer(m.get(2),rows),c2=integer(m.get(3),Math.max(0,columns-1));if(r1>r2||c1>c2||columns==0||(long)(r2-r1+1)*(c2-c1+1)>SheetRules.MAX_CELLS)throw Problem.invalid("병합 범위를 확인하세요.");for(int r=r1;r<=r2;r++){int start=r*columns+c1,end=r*columns+c2+1;int used=occupied.nextSetBit(start);if(used>=0&&used<end)throw Problem.invalid("겹치는 병합 범위가 있습니다.");occupied.set(start,end);}merges.add(List.of(r1,c1,r2,c2));}}
        return Map.of("styles",styles,"cellStyles",cellStyles,"merges",merges);
    }
    public static Map<String,Object> view(Object raw,Set<String> ids){
        if(!(raw instanceof Map<?,?> v))throw Problem.invalid("보기 설정을 확인하세요.");
        String search=Objects.toString(v.get("search"),""),column=Objects.toString(v.get("column"),""),sort=Objects.toString(v.get("sort"),""),direction=Objects.toString(v.get("direction"),"asc");
        if(search.length()>200||!column.isEmpty()&&!ids.contains(column)||!sort.isEmpty()&&!ids.contains(sort)||!Set.of("asc","desc").contains(direction))throw Problem.invalid("검색·정렬 열을 확인하세요.");
        return Map.of("search",search,"column",column,"sort",sort,"direction",direction);
    }
}
