package com.axplatform.policy;

import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class PolicyService {
    public record Draft(long baseRevision,Map<String,List<String>> grants,String reason,String requestKey,Long sourceVersion) {
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ActionRegistry registry;
    private final JsonCodec json;
    private final Clock clock;
    private final AccountSessionService sessions;
    public PolicyService(JdbcTemplate db,TransactionTemplate tx,ActionRegistry registry,JsonCodec json,Clock clock,AccountSessionService sessions) {
        this.db=db;
        this.tx=tx;
        this.registry=registry;
        this.json=json;
        this.clock=clock;
        this.sessions=sessions;
    }
    public void administrator() {
        SessionSubject s=Subjects.current();
        if(!s.administrator()||!sessions.current(s))throw new Problem(401,"SESSION_EXPIRED","최신 관리자 인증이 필요합니다.");
    }
    public Map<String,Object> state(boolean lock) {
        return db.queryForMap("SELECT revision,active_policy_id FROM ap_policy_state WHERE id=1"+(lock?" FOR UPDATE":""));
    }
    public Map<String,PermissionRules.Action> rules() {
        Map<String,PermissionRules.Action> out=new LinkedHashMap<>();
        for(var a:catalog()) {
            String code=(String)a.get("code");
            boolean active="ACTIVE".equals(a.get("status"))&&"ACTIVE".equals(a.get("group_status"))&&registry.implemented(code,(String)a.get("binding_key"));
            out.put(code,new PermissionRules.Action(code,active,Boolean.TRUE.equals(a.get("admin_only"))||((a.get("admin_only") instanceof Number n)&&n.intValue()!=0),(List<String>)a.get("dependencies")));
        }
        return out;
    }
    public List<String> dependencies(String code) {
        return db.queryForList("SELECT depends_on FROM ap_permission_dependency WHERE action_code=? ORDER BY depends_on",String.class,code);
    }
    public List<Map<String,Object>> catalog() {
        var rows=db.queryForList("SELECT a.*,g.status AS group_status,g.name AS group_name FROM ap_permission_action a JOIN ap_feature_group g ON g.code=a.group_code ORDER BY g.sort_order,a.sort_order,a.code");
        Map<String,List<String>> deps=new HashMap<>();
        for(var d:db.queryForList("SELECT action_code,depends_on FROM ap_permission_dependency ORDER BY action_code,depends_on"))deps.computeIfAbsent((String)d.get("action_code"),k->new ArrayList<>()).add((String)d.get("depends_on"));
        for(var a:rows) {
            a.put("dependencies",deps.getOrDefault((String)a.get("code"),List.of()));
            a.put("implemented",registry.implemented((String)a.get("code"),(String)a.get("binding_key")));
        }
        return rows;
    }
    public Map<String,List<String>> grants(long version) {
        Map<String,List<String>> out=new LinkedHashMap<>();
        for(String tier:PermissionRules.TIERS)out.put(tier,db.queryForList("SELECT action_code FROM ap_policy_grant WHERE policy_version_id=? AND tier_code=? ORDER BY sort_order,action_code",String.class,version,tier));
        return out;
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")  public Map<String,Object> snapshot() {
        return tx.execute(t-> {
            var s=state(true);
            administrator();
            return Map.of("revision",s.get("revision"),"versionId",s.get("active_policy_id"),"grants",grants(Inputs.number(s,"active_policy_id")),"catalog",catalog(),"groups",db.queryForList("SELECT * FROM ap_feature_group ORDER BY sort_order,code"),"bindings",registry.bindings());
        }
        );
    }
    private Map<String,List<String>> checked(Draft d) {
        Inputs.text(d.reason(),500,"변경 사유");
        Map<String,List<String>> normalized=new LinkedHashMap<>();
        if(d.grants()==null)throw Problem.invalid("등급별 배정이 필요합니다.");
        for(String tier:PermissionRules.TIERS) {
            var list=d.grants().get(tier);
            normalized.put(tier,list==null?null:new ArrayList<>(list));
        }
        if(!d.grants().keySet().equals(normalized.keySet()))throw Problem.invalid("네 등급을 모두 지정해 주세요.");
        try {
            PermissionRules.validate(rules(),normalized);
        }
        catch(IllegalArgumentException e) {
            throw new Problem(422,"POLICY_DEPENDENCY",e.getMessage());
        }
        return normalized;
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")  public Map<String,Object> preview(Draft d) {
        return tx.execute(t-> {
            var current=state(true);
            administrator();
            if(Inputs.number(current,"revision")!=d.baseRevision())throw Problem.conflict("다른 관리자가 변경했습니다. 최신 정책을 다시 불러오세요.");
            var proposed=checked(d);
            var old=grants(Inputs.number(current,"active_policy_id"));
            var diff=new LinkedHashMap<String,Object>();
            for(String tier:PermissionRules.TIERS) {
                var add=new ArrayList<>(proposed.get(tier));
                add.removeAll(old.get(tier));
                var remove=new ArrayList<>(old.get(tier));
                remove.removeAll(proposed.get(tier));
                diff.put(tier,Map.of("added",add,"removed",remove));
            }
            return Map.of("revision",current.get("revision"),"diff",diff,"affectedWorkspaces",affected(old,proposed),"reason",d.reason(),"jobsImplemented",false);
        }
        );
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")  public Map<String,Object> apply(Draft d) {
        if(d.grants()==null||!d.grants().keySet().equals(new java.util.HashSet<>(PermissionRules.TIERS)))throw Problem.invalid("네 등급을 모두 지정해 주세요.");
        String key=Inputs.text(d.requestKey(),100,"요청 식별값");
        if(!key.matches("[A-Za-z0-9_.:-]{8,100}"))throw Problem.invalid("요청 식별값을 확인해 주세요.");
        long actor=Subjects.current().accountId();
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("revision",d.baseRevision());
        payload.put("reason",d.reason());
        payload.put("sourceVersion",d.sourceVersion());
        var canonical=new LinkedHashMap<String,Object>();
        for(String tier:PermissionRules.TIERS)canonical.put(tier,d.grants()==null?null:d.grants().get(tier));
        payload.put("grants",canonical);
        String hash=Crypto.sha256(json.write(payload));
        return tx.execute(status-> {
            var s=state(true);
            administrator();
            var prior=db.queryForList("SELECT payload_hash,result_version_id FROM ap_policy_request WHERE actor_account_id=? AND request_key=?",actor,key);
            if(!prior.isEmpty()) {
                if(!hash.equals(prior.get(0).get("payload_hash")))throw Problem.conflict("같은 요청 식별값에 다른 변경을 사용할 수 없습니다.");
                return version(Inputs.number(prior.get(0),"result_version_id"));
            }
            if(Inputs.number(s,"revision")!=d.baseRevision())throw Problem.conflict("정책이 변경되었습니다. 다시 불러온 뒤 적용해 주세요.");
            var selected=checked(d);
            if(d.sourceVersion()!=null&&db.queryForObject("SELECT COUNT(*) FROM ap_policy_version WHERE id=?",Integer.class,d.sourceVersion())!=1)throw Problem.invalid("복원할 이력을 확인해 주세요.");
            if(d.sourceVersion()!=null&&!selected.equals(grants(d.sourceVersion())))throw Problem.invalid("복원 내용은 선택한 이력과 일치해야 합니다.");
            long id=saveSnapshot(s,selected,d.sourceVersion()==null?"APPLY":"RESTORE",d.reason(),d.sourceVersion(),actor);
            db.update("INSERT INTO ap_policy_request(actor_account_id,request_key,payload_hash,result_version_id) VALUES(?,?,?,?)",actor,key,hash,id);
            return version(id);
        }
        );
    }
    private long affected(Map<String,List<String>> before,Map<String,List<String>> after) {
        Set<String> changed=new HashSet<>();
        for(String tier:PermissionRules.TIERS)if(!new HashSet<>(before.get(tier)).equals(new HashSet<>(after.get(tier))))changed.add(tier);
        if(changed.isEmpty())return 0;
        var rows=db.queryForList("SELECT w.plan_code,o.tier_code,o.expires_at FROM ap_workspace w LEFT JOIN ap_workspace_tier_override o ON o.workspace_id=w.id WHERE w.kind='CUSTOMER'");
        long count=0;
        var now=LocalDateTime.now(clock);
        for(var row:rows) {
            String tier=null;
            if(row.get("tier_code")!=null&&(row.get("expires_at")==null||((java.sql.Timestamp)row.get("expires_at")).toLocalDateTime().isAfter(now)))tier=(String)row.get("tier_code");
            if(tier==null)tier=switch(String.valueOf(row.get("plan_code"))) {
                case "BASIC"->"TIER1";
                case "PRO"->"TIER2";
                case "MAX"->"TIER3";
                default->"GUEST";
            }
            ;
            if(changed.contains(tier))count++;
        }
        return count;
    }
    private Map<String,Object> version(long id) {
        return db.queryForMap("SELECT id,revision,kind,reason,source_version_id,created_at FROM ap_policy_version WHERE id=?",id);
    }
    public long saveSnapshot(Map<String,Object> s,Map<String,List<String>> selected,String kind,String reason,Long source,long actor) {
        administrator();
        long revision=Inputs.number(s,"revision")+1;
        var now=LocalDateTime.now(clock);
        db.update("INSERT INTO ap_policy_version(revision,actor_account_id,kind,reason,source_version_id,created_at) VALUES(?,?,?,?,?,?)",revision,actor,kind,reason,source,now);
        long id=db.queryForObject("SELECT id FROM ap_policy_version WHERE revision=?",Long.class,revision);
        for(String tier:PermissionRules.TIERS) {
            int order=0;
            for(String code:selected.get(tier))db.update("INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) VALUES(?,?,?,?)",id,tier,code,order++);
        }
        db.update("UPDATE ap_policy_state SET revision=?,active_policy_id=? WHERE id=1",revision,id);
        db.update("INSERT INTO ap_admin_audit(actor_account_id,action,detail,created_at) VALUES(?,'POLICY_CHANGED',?,?)",actor,"정책 "+revision+" 변경. 상세 사유는 정책 이력에 저장됨.",now);
        return id;
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')") public List<Map<String,Object>> history() {
        return db.queryForList("SELECT id,revision,actor_account_id,kind,reason,source_version_id,created_at FROM ap_policy_version ORDER BY revision DESC LIMIT 100");
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')") public Map<String,Object> historical(long id) {
        return Map.of("version",version(id),"grants",grants(id));
    }
}
