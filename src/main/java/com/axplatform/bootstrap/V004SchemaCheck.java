package com.axplatform.bootstrap;

import java.util.*;
import com.axplatform.policy.ActionRegistry;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @Order(-8)
public class V004SchemaCheck implements ApplicationRunner {
    private final JdbcTemplate db;private final ActionRegistry registry;
    public V004SchemaCheck(JdbcTemplate db,ActionRegistry registry){this.db=db;this.registry=registry;}
    public void run(ApplicationArguments args){try{
        if(db.queryForObject("SELECT COUNT(*) FROM ap_schema_migration WHERE version='V004'",Integer.class)!=1)throw new IllegalStateException("V004 미적용");
        db.queryForList("SELECT id,draft_id,name,config_json,version,created_at,updated_at FROM ap_sheet_view WHERE 1=0");
        for(String a:List.of("workspace.sheet.import","workspace.sheet.formula.update","workspace.sheet.analysis.read","workspace.sheet.analysis.manage")){
            var rows=db.queryForList("SELECT binding_key FROM ap_permission_action WHERE code=?",a);if(rows.isEmpty()||rows.get(0).get("binding_key")==null)continue;
            if(!a.equals(rows.get(0).get("binding_key")))throw new IllegalStateException("행동 연결 불일치: "+a);
            for(String d:registry.requiredDependencies(a))if(db.queryForObject("SELECT COUNT(*) FROM ap_permission_dependency WHERE action_code=? AND depends_on=?",Integer.class,a,d)!=1)throw new IllegalStateException("필수 의존 누락: "+a);
        }
    }catch(RuntimeException e){throw new IllegalStateException("앱을 중지하고 HeidiSQL에서 08_V004__guided_workspace.sql을 실행한 뒤 09_VERIFY_V004.sql로 확인하세요. 기존 DB를 삭제하지 마세요.",e);}}
}
