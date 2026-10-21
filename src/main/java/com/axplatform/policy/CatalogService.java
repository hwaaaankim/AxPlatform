package com.axplatform.policy;

import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
@Service @PreAuthorize("hasRole('SUPER_ADMIN')") public class CatalogService {
    public record GroupInput(String code,String name,String status,int sortOrder,long version,String reason) {
    }
    public record ActionInput(String code,String groupCode,String name,String description,String status,String bindingKey,boolean adminOnly,boolean risky,int sortOrder,long version,List<String> dependencies,String reason) {
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final PolicyService policies;
    private final ActionRegistry bindings;
    private final Clock clock;
    public CatalogService(JdbcTemplate db,TransactionTemplate tx,PolicyService policies,ActionRegistry bindings,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.policies=policies;
        this.bindings=bindings;
        this.clock=clock;
    }
    public void group(GroupInput i,boolean create) {
        String code=Inputs.code(i.code());
        if(code.length()>32)throw Problem.invalid("그룹 코드가 너무 깁니다.");
        String name=Inputs.text(i.name(),100,"그룹명");
        Inputs.state(i.status(),Set.of("ACTIVE","PREPARING","DISABLED"));
        String reason=Inputs.text(i.reason(),500,"변경 사유");
        tx.executeWithoutResult(t-> {
            var s=policies.state(true);
            var old=db.queryForList("SELECT * FROM ap_feature_group WHERE code=?",code);
            if(create) {
                if(!old.isEmpty())throw Problem.conflict("이미 있는 그룹 코드입니다.");
                db.update("INSERT INTO ap_feature_group(code,name,status,sort_order) VALUES(?,?,?,?)",code,name,i.status(),i.sortOrder());
            }
            else {
                if(old.isEmpty())throw new Problem(404,"NOT_FOUND","그룹을 찾지 못했습니다.");
                if(Inputs.number(old.get(0),"revision")!=i.version())throw Problem.conflict("그룹이 수정되었습니다.");
                db.update("UPDATE ap_feature_group SET name=?,status=?,sort_order=?,revision=revision+1 WHERE code=?",name,i.status(),i.sortOrder(),code);
            }
            saveCatalog(s,reason);
        }
        );
    }
    public void action(ActionInput i,boolean create) {
        String code=Inputs.code(i.code()),name=Inputs.text(i.name(),100,"항목명"),group=Inputs.text(i.groupCode(),32,"그룹");
        String reason=Inputs.text(i.reason(),500,"변경 사유");
        String description=i.description()==null?"":i.description().strip();
        if(description.length()>500)throw Problem.invalid("설명은 500자 이내입니다.");
        Inputs.state(i.status(),Set.of("ACTIVE","PREPARING","DISABLED"));
        List<String> dependencies=i.dependencies()==null?List.of():i.dependencies();
        if(dependencies.size()>100||new HashSet<>(dependencies).size()!=dependencies.size())throw Problem.invalid("의존 항목을 확인해 주세요.");
        var missingRequired = new TreeSet<>(bindings.requiredDependencies(code));
        missingRequired.removeAll(dependencies);
        if (!missingRequired.isEmpty()) {
            throw new Problem(422,"REQUIRED_DEPENDENCY",
                "이 서버 기능의 필수 의존 항목은 제거할 수 없습니다. 필요한 항목을 함께 선택해 주세요: " + String.join(", ",missingRequired),
                Map.of("action",code,"requiredDependencies",List.copyOf(missingRequired)));
        }
        String binding=i.bindingKey()==null||i.bindingKey().isBlank()?null:i.bindingKey();
        if(binding!=null&&!bindings.implemented(code,binding))throw Problem.invalid("등록된 동일 서버 행동 코드만 연결할 수 있습니다.");
        if("ACTIVE".equals(i.status())&&binding==null)throw Problem.invalid("서버 행동이 미연결인 항목은 준비 중으로 저장하세요.");
        tx.executeWithoutResult(t-> {
            var s=policies.state(true);
            var old=db.queryForList("SELECT * FROM ap_permission_action WHERE code=?",code);
            var now=LocalDateTime.now(clock);
            if(db.queryForObject("SELECT COUNT(*) FROM ap_feature_group WHERE code=? AND status<>'ARCHIVED'",Integer.class,group)!=1)throw Problem.invalid("그룹을 확인해 주세요.");
            if(create) {
                if(!old.isEmpty())throw Problem.conflict("이미 있는 기능 코드입니다.");
                db.update("INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",code,group,name,description,binding,i.status(),i.adminOnly(),i.risky(),i.sortOrder(),now,now);
            }
            else {
                if(old.isEmpty())throw new Problem(404,"NOT_FOUND","기능을 찾지 못했습니다.");
                if(Inputs.number(old.get(0),"version")!=i.version())throw Problem.conflict("기능 정의가 수정되었습니다.");
                if(!Objects.equals(old.get(0).get("binding_key"),binding))throw Problem.invalid("기존 서버 행동의 연결 의미는 바꿀 수 없습니다.");
                db.update("UPDATE ap_permission_action SET group_code=?,name=?,description=?,status=?,admin_only=?,risky=?,sort_order=?,version=version+1,updated_at=? WHERE code=?",group,name,description,i.status(),i.adminOnly(),i.risky(),i.sortOrder(),now,code);
            }
            db.update("DELETE FROM ap_permission_dependency WHERE action_code=?",code);
            for(String dep:dependencies) {
                Inputs.code(dep);
                db.update("INSERT INTO ap_permission_dependency(action_code,depends_on) VALUES(?,?)",code,dep);
            }
            saveCatalog(s,reason);
        }
        );
    }
    private void saveCatalog(Map<String,Object> s,String reason) {
        var current=policies.grants(Inputs.number(s,"active_policy_id"));
        try {
            PermissionRules.validate(policies.rules(),current);
        }
        catch(IllegalArgumentException e) {
            throw new Problem(422,"CATALOG_IMPACT","현재 배정과 충돌합니다. 먼저 배정·의존성을 조정해 주세요. "+e.getMessage());
        }
        policies.saveSnapshot(s,current,"CATALOG",reason,null,Subjects.current().accountId());
    }
    public Map<String,Object> impact(String code) {
        var state=policies.state(false);
        return Map.of("activeGrants",db.queryForObject("SELECT COUNT(*) FROM ap_policy_grant WHERE policy_version_id=? AND action_code=?",Long.class,state.get("active_policy_id"),code),"dependencies",db.queryForList("SELECT action_code FROM ap_permission_dependency WHERE depends_on=?",String.class,code),"historyReferences",db.queryForObject("SELECT COUNT(*) FROM ap_policy_grant WHERE action_code=?",Long.class,code));
    }
    public void removeAction(String code,long expected,String reason) {
        Inputs.code(code);
        Inputs.text(reason,500,"삭제 사유");
        tx.executeWithoutResult(t-> {
            var state=policies.state(true);
            var old=db.queryForList("SELECT * FROM ap_permission_action WHERE code=?",code);
            if(old.isEmpty())throw new Problem(404,"NOT_FOUND","항목을 찾지 못했습니다.");
            if(Inputs.number(old.get(0),"version")!=expected)throw Problem.conflict("항목이 변경되었습니다.");
            var impact=impact(code);
            if(((Number)impact.get("activeGrants")).longValue()>0||!((List<?>)impact.get("dependencies")).isEmpty())throw new Problem(409,"CATALOG_REFERENCED","배정·의존성을 먼저 해제해 주세요.",impact);
            boolean unused=((Number)impact.get("historyReferences")).longValue()==0&&old.get(0).get("binding_key")==null;
            if(unused) {
                db.update("DELETE FROM ap_permission_dependency WHERE action_code=?",code);
                db.update("DELETE FROM ap_permission_action WHERE code=?",code);
            }
            else db.update("UPDATE ap_permission_action SET status='ARCHIVED',version=version+1,updated_at=? WHERE code=?",LocalDateTime.now(clock),code);
            saveCatalog(state,reason);
        }
        );
    }
    public void removeGroup(String code,long expected,String reason) {
        Inputs.code(code);
        Inputs.text(reason,500,"그룹 삭제 사유");
        tx.executeWithoutResult(t-> {
            var s=policies.state(true);
            var old=db.queryForList("SELECT * FROM ap_feature_group WHERE code=?",code);
            if(old.isEmpty())throw new Problem(404,"NOT_FOUND","그룹을 찾지 못했습니다.");
            if(Inputs.number(old.get(0),"revision")!=expected)throw Problem.conflict("그룹이 수정되었습니다.");
            if(db.queryForObject("SELECT COUNT(*) FROM ap_permission_action WHERE group_code=?",Long.class,code)>0)throw Problem.conflict("항목을 다른 그룹으로 이동하거나 정리한 후 삭제하세요.");
            db.update("DELETE FROM ap_feature_group WHERE code=?",code);
            saveCatalog(s,reason);
        }
        );
    }
}
