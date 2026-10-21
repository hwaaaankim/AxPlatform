package com.axplatform.policy;

import java.util.*;
/** Pure server rules; no implied tier inheritance or client-supplied behavior bindings. */
public final class PermissionRules {
    public static final List<String> TIERS=List.of("GUEST","TIER1","TIER2","TIER3");
    public record Action(String code,boolean available,boolean administratorOnly,List<String> dependencies) {
    }
    private PermissionRules() {
    }
    public static void validate(Map<String,Action> catalog,Map<String,List<String>> grants) {
        if(!grants.keySet().equals(new HashSet<>(TIERS)))throw new IllegalArgumentException("네 등급의 독립된 배정 집합이 필요합니다.");
        cycle(catalog);
        for(String tier:TIERS) {
            List<String> list=grants.get(tier);
            if(list==null||list.size()>1000||new HashSet<>(list).size()!=list.size())throw new IllegalArgumentException("중복된 권한 또는 너무 많은 항목이 있습니다.");
            Set<String> selected=new HashSet<>(list);
            for(String code:list) {
                Action a=catalog.get(code);
                if(a==null||!a.available()||a.administratorOnly())throw new IllegalArgumentException(code+": 아직 구현·활성되지 않았거나 관리 전용인 항목입니다.");
                for(String dep:a.dependencies())if(!selected.contains(dep))throw new IllegalArgumentException(code+"에 필요한 권한: "+dep);
            }
        }
    }
    public static void cycle(Map<String,Action> catalog) {
        Set<String> done=new HashSet<>(),visiting=new HashSet<>();
        for(String c:catalog.keySet())visit(c,catalog,done,visiting);
    }
    private static void visit(String code,Map<String,Action> catalog,Set<String> done,Set<String> visiting) {
        if(done.contains(code))return;
        if(!visiting.add(code))throw new IllegalArgumentException("권한 의존성이 순환합니다: "+code);
        Action a=catalog.get(code);
        if(a==null)throw new IllegalArgumentException("존재하지 않는 의존 권한: "+code);
        for(String dep:a.dependencies())visit(dep,catalog,done,visiting);
        visiting.remove(code);
        done.add(code);
    }
    public static Set<String> closure(String code,Map<String,Action> catalog) {
        Set<String> out=new LinkedHashSet<>();
        close(code,catalog,out,new HashSet<>());
        return out;
    }
    private static void close(String code,Map<String,Action> catalog,Set<String> out,Set<String> visiting) {
        if(visiting.contains(code))throw new IllegalArgumentException("권한 의존성이 순환합니다.");
        if(out.contains(code))return;
        Action action=catalog.get(code);
        if(action==null)throw new IllegalArgumentException("등록되지 않은 코드");
        visiting.add(code);out.add(code);
        for(String dep:action.dependencies())close(dep,catalog,out,visiting);
        visiting.remove(code);
    }
    public static boolean permitted(String code,Map<String,Action> catalog,Set<String> granted,boolean administrator) {
        try {
            Set<String> needed=closure(code,catalog);
            for(String key:needed) {
                Action action=catalog.get(key);
                if(!action.available()||(!administrator&&action.administratorOnly()))return false;
            }
            return administrator||granted.containsAll(needed);
        } catch(IllegalArgumentException invalid) { return false; }
    }
}
