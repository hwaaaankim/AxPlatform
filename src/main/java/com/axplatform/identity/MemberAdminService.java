package com.axplatform.identity;

import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
@Service @PreAuthorize("hasRole('SUPER_ADMIN')") public class MemberAdminService {
    public record Change(String name,String email,String phone,String status,long version,String reason) {
    }
    public record TierChange(String tier,String expiresAt,long workspaceVersion,String reason) {
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final PolicyService policies;
    private final AccessService access;
    private final JsonCodec json;
    private final Clock clock;
    public MemberAdminService(JdbcTemplate db,TransactionTemplate tx,PolicyService policies,AccessService access,JsonCodec json,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.policies=policies;
        this.access=access;
        this.json=json;
        this.clock=clock;
    }
    public Map<String,Object> detail(long id) {
        var rows=db.queryForList("SELECT id,account_type,system_role,status,display_name,contact_email,phone_e164,version,created_at,withdrawn_at FROM ap_account WHERE id=?",id);
        if(rows.isEmpty())throw new Problem(404,"MEMBER_NOT_FOUND","회원을 찾지 못했습니다.");
        var account=rows.get(0);
        String method="SYSTEM_ADMIN".equals(account.get("account_type"))?"ADMIN":db.queryForObject("SELECT CASE WHEN e.account_id IS NOT NULL THEN 'EMAIL' ELSE x.provider END FROM ap_account a LEFT JOIN ap_email_credential e ON e.account_id=a.id LEFT JOIN ap_external_identity x ON x.account_id=a.id WHERE a.id=?",String.class,id);
        var spaces=db.queryForList("SELECT w.id,w.name,w.kind,w.plan_code,w.version,o.tier_code AS manual_tier,o.expires_at AS manual_expires,o.reason AS manual_reason FROM ap_workspace w LEFT JOIN ap_workspace_tier_override o ON o.workspace_id=w.id WHERE w.owner_account_id=? ORDER BY w.id",id);
        for(var w:spaces)w.put("effective_tier",access.tier(Inputs.number(w,"id")));
        return Map.of("account",account,"loginMethod",method==null?"UNKNOWN":method,"workspaces",spaces,"history",db.queryForList("SELECT actor_account_id,action,reason,before_json,after_json,created_at FROM ap_member_change WHERE account_id=? ORDER BY id DESC LIMIT 50",id));
    }
    public void change(long id,Change change) {
        String name=Inputs.text(change.name(),100,"이름");
        String state=Inputs.state(change.status(),Set.of("ACTIVE","SUSPENDED","WITHDRAWN"));
        String reason=Inputs.text(change.reason(),500,"변경 사유");
        tx.executeWithoutResult(t-> {
            policies.state(true);
            policies.administrator();
            var found=db.queryForList("SELECT * FROM ap_account WHERE id=? FOR UPDATE",id);
            if(found.isEmpty())throw new Problem(404,"MEMBER_NOT_FOUND","회원을 찾지 못했습니다.");
            var old=found.get(0);
            if(Inputs.number(old,"version")!=change.version())throw Problem.conflict("회원 정보가 변경되었습니다. 다시 불러오세요.");
            boolean admin="SYSTEM_ADMIN".equals(old.get("account_type"));
            if(admin&&"ACTIVE".equals(old.get("status"))&&!"ACTIVE".equals(state)) {
                var active=db.queryForList("SELECT id FROM ap_account WHERE account_type='SYSTEM_ADMIN' AND system_role='SUPER_ADMIN' AND status='ACTIVE' FOR UPDATE");
                if(active.size()<=1)throw new Problem(409,"LAST_ADMIN","마지막 활성 최고관리자는 정지·탈퇴·강등할 수 없습니다.");
            }
            String email=admin?(String)old.get("contact_email"):Inputs.email(change.email()),phone=admin?(String)old.get("phone_e164"):Inputs.phone(change.phone());
            boolean statusChanged=!state.equals(old.get("status"));
            var now=LocalDateTime.now(clock);
            db.update("UPDATE ap_account SET display_name=?,contact_email=?,phone_e164=?,status=?,security_version=security_version+?,withdrawn_at=?,updated_at=?,version=version+1 WHERE id=?",name,email,phone,state,statusChanged?1:0,"WITHDRAWN".equals(state)?now:null,now,id);
            if(admin&&statusChanged)db.update("UPDATE ap_admin_credential SET security_version=security_version+1 WHERE account_id=?",id);
            Map<String,Object> before=new LinkedHashMap<>();
            for(String key:List.of("display_name","contact_email","phone_e164","status"))before.put(key,old.get(key));
            Map<String,Object> after=new LinkedHashMap<>();
            after.put("display_name",name);
            after.put("contact_email",email);
            after.put("phone_e164",phone);
            after.put("status",state);
            db.update("INSERT INTO ap_member_change(account_id,actor_account_id,action,reason,before_json,after_json,created_at) VALUES(?,?,'MEMBER_UPDATED',?,?,?,?)",id,Subjects.current().accountId(),reason,json.write(before),json.write(after),now);
        }
        );
    }
    public void tier(long workspace,TierChange i) {
        String reason=Inputs.text(i.reason(),500,"등급 변경 사유");
        boolean clear=i.tier()==null||i.tier().isBlank();
        if(!clear&&!PermissionRules.TIERS.contains(i.tier()))throw Problem.invalid("등급을 확인해 주세요.");
        LocalDateTime expires=null;
        if(i.expiresAt()!=null&&!i.expiresAt().isBlank()) {
            try {
                expires=LocalDateTime.ofInstant(Instant.parse(i.expiresAt()),ZoneOffset.UTC);
            }
            catch(Exception e) {
                throw Problem.invalid("만료 일시를 확인해 주세요.");
            }
            if(!expires.isAfter(LocalDateTime.now(clock)))throw Problem.invalid("만료는 현재 이후로 지정하세요.");
        }
        final LocalDateTime expiry=expires;
        tx.executeWithoutResult(t-> {
            policies.state(true);
            policies.administrator();
            var spaces=db.queryForList("SELECT * FROM ap_workspace WHERE id=? FOR UPDATE",workspace);
            if(spaces.isEmpty())throw new Problem(404,"WORKSPACE_NOT_FOUND","공간을 찾지 못했습니다.");
            var w=spaces.get(0);
            if(!"CUSTOMER".equals(w.get("kind")))throw Problem.invalid("관리자 시험 공간에는 상품 등급을 배정하지 않습니다.");
            if(Inputs.number(w,"version")!=i.workspaceVersion())throw Problem.conflict("공간·등급이 변경되었습니다.");
            var now=LocalDateTime.now(clock);
            db.update("DELETE FROM ap_workspace_tier_override WHERE workspace_id=?",workspace);
            if(!clear)db.update("INSERT INTO ap_workspace_tier_override(workspace_id,tier_code,reason,expires_at,actor_account_id,updated_at) VALUES(?,?,?,?,?,?)",workspace,i.tier(),reason,expiry,Subjects.current().accountId(),now);
            db.update("UPDATE ap_workspace SET version=version+1,updated_at=? WHERE id=?",now,workspace);
            db.update("INSERT INTO ap_workspace_tier_history(workspace_id,actor_account_id,action,tier_code,reason,expires_at,created_at) VALUES(?,?,?,?,?,?,?)",workspace,Subjects.current().accountId(),clear?"CLEAR":"OVERRIDE",clear?null:i.tier(),reason,expiry,now);
            access.event(workspace,null,"tier.changed",String.valueOf(workspace),i.workspaceVersion()+1);
        }
        );
    }
    public List<Map<String,Object>> tierHistory(long workspace) {
        return db.queryForList("SELECT actor_account_id,action,tier_code,reason,expires_at,created_at FROM ap_workspace_tier_history WHERE workspace_id=? ORDER BY id DESC LIMIT 100",workspace);
    }
}
