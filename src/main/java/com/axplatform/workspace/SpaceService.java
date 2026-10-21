package com.axplatform.workspace;

import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
@Service public class SpaceService {
    public record ScopeInput(long workspaceId,String name,long version,boolean archived) {
    }
    private final JdbcTemplate db;
    private final AccessService access;
    private final AdminCredentialStore keys;
    private final Clock clock;
    private final JsonCodec json;
    public SpaceService(JdbcTemplate db,AccessService access,AdminCredentialStore keys,Clock clock,JsonCodec json) {
        this.db=db;
        this.access=access;
        this.keys=keys;
        this.clock=clock;
        this.json=json;
    }
    public List<Map<String,Object>> spaces(SessionSubject s) {
        var rows=db.queryForList("SELECT id,name,kind,plan_code,version FROM ap_workspace WHERE owner_account_id=? AND kind=? ORDER BY id",s.accountId(),s.administrator()?"ADMIN_TEST":"CUSTOMER");
        for(var w:rows) {
            access.member(s,Inputs.number(w,"id"));
            w.put("effective_tier",s.administrator()?"ADMIN":access.tier(Inputs.number(w,"id")));
        }
        return rows;
    }
    public List<Map<String,Object>> scopes(SessionSubject s,long workspace) {
        return access.workspace(s,workspace,List.of("workspace.scope.read"),()->db.queryForList("SELECT id,workspace_id,name,is_default,version,archived_at FROM ap_business_scope WHERE workspace_id=? ORDER BY is_default DESC,id",workspace));
    }
    public Map<String,Object> create(SessionSubject s,ScopeInput i) {
        String name=Inputs.text(i.name(),100,"사업명");
        return access.workspace(s,i.workspaceId(),List.of("workspace.scope.create"),()-> {
            var now=LocalDateTime.now(clock);
            long id=keys.insert("INSERT INTO ap_business_scope(workspace_id,name,is_default,version,created_at,updated_at) VALUES(?,?,FALSE,0,?,?)",i.workspaceId(),name,now,now);
            access.event(i.workspaceId(),id,"scope.created",String.valueOf(id),0);
            return Map.of("id",id,"version",0);
        }
        );
    }
    public Map<String,Object> change(SessionSubject s,long id,ScopeInput i,boolean archive) {
        return access.business(s,id,true,List.of(archive?"workspace.scope.archive":"workspace.scope.update"),()-> {
            var row=db.queryForMap("SELECT * FROM ap_business_scope WHERE id=?",id);
            if(Inputs.number(row,"version")!=i.version())throw Problem.conflict("사업이 변경되었습니다.");
            if(archive) {
                if(i.archived()&&(Boolean.TRUE.equals(row.get("is_default"))||(row.get("is_default") instanceof Number n&&n.intValue()!=0)))throw Problem.invalid("기본 사업은 보관할 수 없습니다.");
                db.update("UPDATE ap_business_scope SET archived_at=?,version=version+1,updated_at=? WHERE id=?",i.archived()?LocalDateTime.now(clock):null,LocalDateTime.now(clock),id);
            }
            else db.update("UPDATE ap_business_scope SET name=?,version=version+1,updated_at=? WHERE id=?",Inputs.text(i.name(),100,"사업명"),LocalDateTime.now(clock),id);
            access.event(Inputs.number(row,"workspace_id"),id,archive?"scope.archived":"scope.updated",String.valueOf(id),i.version()+1);
            return Map.of("id",id,"version",i.version()+1);
        }
        );
    }
    public Map<String,Object> dashboard(SessionSubject s,long workspace) {
        return access.workspace(s,workspace,List.of("workspace.dashboard.read"),()-> {
            var rows=db.queryForList("SELECT widgets_json,version FROM ap_workspace_dashboard WHERE workspace_id=?",workspace);
            return Map.of("widgets",rows.isEmpty()?List.of("EXCEL","WORD","HWP","PPT","CRM","CALENDAR","APP"):json.value((String)rows.get(0).get("widgets_json")),"version",rows.isEmpty()?0:rows.get(0).get("version"),"capabilities",access.allowed(s,workspace));
        }
        );
    }
    public Map<String,Object> dashboardSave(SessionSubject s,long workspace,List<String> widgets,long expected) {
        if(widgets==null||widgets.size()>7||new HashSet<>(widgets).size()!=widgets.size()||!DraftService.WORKFLOWS.containsAll(widgets))throw Problem.invalid("홈에 표시할 업무를 확인해 주세요.");
        return access.workspace(s,workspace,List.of("workspace.dashboard.manage"),()-> {
            var rows=db.queryForList("SELECT version FROM ap_workspace_dashboard WHERE workspace_id=?",workspace);
            long actual=rows.isEmpty()?0:Inputs.number(rows.get(0),"version");
            if(actual!=expected)throw Problem.conflict("홈 구성이 변경되었습니다.");
            if(rows.isEmpty())db.update("INSERT INTO ap_workspace_dashboard(workspace_id,widgets_json,version) VALUES(?,?,1)",workspace,json.write(widgets));
            else db.update("UPDATE ap_workspace_dashboard SET widgets_json=?,version=version+1 WHERE workspace_id=?",json.write(widgets),workspace);
            access.event(workspace,null,"dashboard.updated",String.valueOf(workspace),actual+1);
            return Map.of("version",actual+1);
        }
        );
    }
}
