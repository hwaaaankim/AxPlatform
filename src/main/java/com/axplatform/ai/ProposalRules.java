package com.axplatform.ai;

import java.util.*;
import com.axplatform.sheet.SheetRules;

/** Pure allowlist validation. Provider JSON is untrusted input, even with strict JSON Schema. */
public final class ProposalRules {
    public static final List<String> FIELDS=List.of("purpose","data","result","details","audience");
    private ProposalRules(){}
    public static Map<String,Object> normalize(Map<String,Object> input,String workflow){
        var keys=new HashSet<>(Set.of("summary","confirmed","assumptions","questions","draft","sheet","warnings","ready"));
        if(input.containsKey("operations"))keys.add("operations");if(input.containsKey("continuation"))keys.add("continuation");exact(input,keys);
        var out=new LinkedHashMap<String,Object>();
        out.put("summary",string(input.get("summary"),2000,true));
        out.put("confirmed",lines(input.get("confirmed"),20,1000));
        out.put("assumptions",lines(input.get("assumptions"),20,1000));
        if(!(input.get("questions") instanceof List<?> questions)||questions.size()>8)throw bad("확인 질문은 최대 8개입니다.");
        List<Map<String,Object>> normalized=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(Object value:questions){
            Map<String,Object> q=object(value);exact(q,Set.of("id","field","title","reason","choices","recommended","required"));
            String id=string(q.get("id"),60,true),field=string(q.get("field"),20,true);
            if(!id.matches("[a-zA-Z0-9_-]{1,60}")||!ids.add(id)||!FIELDS.contains(field))throw bad("질문 참조를 확인해 주세요.");
            List<String> choices=lines(q.get("choices"),8,500);String recommended=string(q.get("recommended"),500,false);
            if(!recommended.isEmpty()&&!choices.contains(recommended))throw bad("추천 답변은 선택지에 포함되어야 합니다.");
            if(!(q.get("required") instanceof Boolean required))throw bad("질문의 필수 여부를 확인해 주세요.");
            normalized.add(Map.of("id",id,"field",field,"title",string(q.get("title"),300,true),"reason",string(q.get("reason"),1000,true),"choices",choices,"recommended",recommended,"required",required));
        }
        out.put("questions",normalized);
        Map<String,Object> draft=object(input.get("draft"));exact(draft,Set.of("title","answers"));
        Map<String,Object> answers=object(draft.get("answers"));exact(answers,new HashSet<>(FIELDS));
        Map<String,Object> cleanAnswers=new LinkedHashMap<>();for(String field:FIELDS)cleanAnswers.put(field,string(answers.get(field),2000,false));
        out.put("draft",Map.of("title",string(draft.get("title"),100,true),"answers",cleanAnswers));
        Object sheet=input.get("sheet");
        if(!"EXCEL".equals(workflow)&&sheet!=null)throw bad("현재 업무에서는 표 결과를 적용할 수 없습니다.");
        try {
            if(sheet==null)out.put("sheet",null);
            else {
                Map<String,Object> table=object(sheet);
                // AI proposals must carry stable row identities; inventing UUIDs here breaks apply idempotency.
                if(!table.keySet().containsAll(Set.of("columns","rows","rowIds"))||!Set.of("columns","rows","rowIds","excel").containsAll(table.keySet()))throw bad("제안 표 항목을 확인하세요.");
                if(table.containsKey("excel")&&!Set.of("formulas").containsAll(object(table.get("excel")).keySet()))throw bad("AI 제안에는 셀 수식만 포함할 수 있습니다.");
                var valid=SheetRules.normalize(com.axplatform.sheet.ExcelCells.aiFormulas(table,0));int rowCount=((List<?>)valid.get("rows")).size(),cols=((List<?>)valid.get("columns")).size();if(rowCount>200||(long)rowCount*cols>4000)throw bad("AI 제안은 최대 200행·4,000셀입니다.");long chars=0;for(Object row:(List<?>)valid.get("rows"))for(Object cell:(List<?>)row)chars+=((String)cell).length();if(chars>80000)throw bad("AI 제안은 한 분량당 80,000자 이하입니다. 같은 위치에서 더 작게 나누어 요청하세요.");out.put("sheet",valid);
            }
        }
        catch(RuntimeException invalid){throw bad("제안 표의 열·행·셀·식별자 형식을 확인해 주세요.");}
        if(input.containsKey("operations")){
            var operations=com.axplatform.sheet.AiSheetOperations.normalize(input.get("operations"));
            if(!operations.isEmpty()&&(!"EXCEL".equals(workflow)||sheet!=null))throw bad("AI 변경 작업은 EXCEL에서 표 전체 제안과 분리해 제출하세요.");
            out.put("operations",operations);
        }
        if(input.containsKey("continuation")){
            if(input.get("continuation")==null)out.put("continuation",null);
            else{var progress=object(input.get("continuation"));exact(progress,Set.of("hasMore","nextRow","note"));if(!(progress.get("hasMore") instanceof Boolean)||!(progress.get("nextRow") instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>1000001)throw bad("이어 처리 위치를 확인하세요.");out.put("continuation",Map.of("hasMore",progress.get("hasMore"),"nextRow",n.intValue(),"note",string(progress.get("note"),2000,false)));}
        }
        out.put("warnings",lines(input.get("warnings"),20,1000));
        if(!(input.get("ready") instanceof Boolean ready))throw bad("제안 준비 상태를 확인해 주세요.");
        if(ready&&normalized.stream().anyMatch(q->Boolean.TRUE.equals(q.get("required"))))throw bad("필수 확인 질문이 남은 제안은 준비 완료로 표시할 수 없습니다.");
        out.put("ready",ready);return out;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object value){
        if(!(value instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k->!(k instanceof String)))throw bad("제안 자료 형식을 확인해 주세요.");
        return (Map<String,Object>)map;
    }
    private static void exact(Map<String,Object> map,Set<String> keys){if(map==null||!map.keySet().equals(keys))throw bad("허용한 제안 항목과 일치하지 않습니다.");}
    private static String string(Object value,int max,boolean required){
        if(!(value instanceof String text)||text.length()>max||(required&&text.isBlank()))throw bad("제안 문자열 길이와 내용을 확인해 주세요.");
        for(int at=0;at<text.length();){int cp=text.codePointAt(at);if(!(cp==9||cp==10||cp==13||(cp>=32&&cp<=0xd7ff)||(cp>=0xe000&&cp<=0xfffd)||(cp>=0x10000&&cp<=0x10ffff)))throw bad("제안 문자열에 저장할 수 없는 문자가 있습니다.");at+=Character.charCount(cp);}return text;
    }
    private static List<String> lines(Object input,int max,int length){if(!(input instanceof List<?> values)||values.size()>max)throw bad("제안 목록 수를 확인해 주세요.");List<String> out=new ArrayList<>();for(Object value:values)out.add(string(value,length,true));return out;}
    private static IllegalArgumentException bad(String text){return new IllegalArgumentException(text);}
}
