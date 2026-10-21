package com.axplatform.sheet;

import com.axplatform.common.*;
import java.math.*;
import java.util.*;

public final class SheetAnalysis {
    private SheetAnalysis() { }
    public static Map<String,Object> config(Map<String,Object> raw) {
        if(raw==null||!Set.of("groupColumn","valueColumn","operation","name","sheetId").containsAll(raw.keySet()))throw Problem.invalid("분석 조건을 확인하세요.");
        String group=Objects.toString(raw.get("groupColumn"),""),value=Objects.toString(raw.get("valueColumn"),""),operation=Objects.toString(raw.get("operation"),"COUNT"),name=Objects.toString(raw.get("name"),"데이터 보기").strip();
        if(!Set.of("COUNT","SUM","AVG","MIN","MAX").contains(operation)||name.isBlank()||name.length()>100||group.length()>40||value.length()>40)throw Problem.invalid("분석 이름·열·연산을 확인하세요.");
        var clean=new LinkedHashMap<String,Object>(Map.of("groupColumn",group,"valueColumn",value,"operation",operation,"name",name));
        if(raw.containsKey("sheetId")){String id=Objects.toString(raw.get("sheetId"),"");if(!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw Problem.invalid("분석할 시트를 선택하세요.");clean.put("sheetId",id);}return clean;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> calculate(Map<String,Object> sheet,Map<String,Object> raw,long version) {
        var c=config(raw);var columns=(List<Map<String,Object>>)sheet.get("columns");var rows=(List<List<String>>)sheet.get("rows");
        String group=(String)c.get("groupColumn"),value=(String)c.get("valueColumn"),operation=(String)c.get("operation");int g=-1,v=-1;
        for(int i=0;i<columns.size();i++){if(columns.get(i).get("id").equals(group))g=i;if(columns.get(i).get("id").equals(value))v=i;}
        if(!group.isEmpty()&&g<0)throw Problem.invalid("묶을 기준 열이 삭제되었거나 없습니다. 분석 조건을 다시 선택하세요.");
        if(!operation.equals("COUNT")&&(v<0||!"NUMBER".equals(columns.get(v).get("type"))))throw Problem.invalid("집계할 숫자 열을 선택하세요.");
        Map<String,List<BigDecimal>> buckets=new LinkedHashMap<>();Map<String,Integer> counts=new LinkedHashMap<>();int blanks=0;
        for(var row:rows){String label=g<0?"전체":row.get(g).isEmpty()?"(빈 값)":row.get(g);buckets.computeIfAbsent(label,k->new ArrayList<>());counts.merge(label,1,Integer::sum);
            if(!operation.equals("COUNT")){String n=row.get(v);if(n.isEmpty())blanks++;else buckets.get(label).add(new BigDecimal(n));}}
        List<Map<String,Object>> points=new ArrayList<>();for(var e:buckets.entrySet()){List<BigDecimal> ns=e.getValue();String number="";
            if(operation.equals("COUNT"))number=String.valueOf(counts.get(e.getKey()));else if(!ns.isEmpty()){BigDecimal n=switch(operation){case "SUM","AVG"->ns.stream().reduce(BigDecimal.ZERO,BigDecimal::add);case "MIN"->ns.stream().min(BigDecimal::compareTo).orElseThrow();case "MAX"->ns.stream().max(BigDecimal::compareTo).orElseThrow();default->throw new IllegalStateException();};if(operation.equals("AVG"))n=n.divide(BigDecimal.valueOf(ns.size()),12,RoundingMode.HALF_UP);number=n.stripTrailingZeros().toPlainString();}
            points.add(Map.of("label",e.getKey(),"value",number,"rows",counts.get(e.getKey()),"numericCount",ns.size()));}
        return Map.of("sheetVersion",version,"rowCount",rows.size(),"blankValues",blanks,"unit",operation.equals("COUNT")?"건":columns.get(v).get("unit"),"points",points,"config",c);
    }
}
