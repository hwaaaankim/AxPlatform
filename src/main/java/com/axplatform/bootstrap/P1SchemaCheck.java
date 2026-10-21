package com.axplatform.bootstrap;

import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component @Order(-10) public class P1SchemaCheck implements ApplicationRunner {
    private final JdbcTemplate db;
    public P1SchemaCheck(JdbcTemplate db) {
        this.db=db;
    }
    @Override public void run(ApplicationArguments args) {
        if(db.queryForObject("SELECT COUNT(*) FROM ap_schema_migration WHERE version='V002'",Integer.class)!=1)throw new IllegalStateException("HeidiSQL에서 추가 SQL 01_V002__p1_foundation.sql을 먼저 실행해 주세요.");
        try {
            db.queryForMap("SELECT revision,active_policy_id FROM ap_policy_state WHERE id=1");
            for(String table:java.util.List.of("ap_auth_rate","ap_verification","ap_password_reset","ap_consent","ap_account_preference","ap_permission_action","ap_permission_dependency","ap_policy_version","ap_policy_grant","ap_policy_request","ap_member_change","ap_workspace_tier_override","ap_workspace_tier_history","ap_workspace_dashboard","ap_work_draft","ap_asset","ap_draft_asset","ap_event_outbox"))db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE 1=0",Long.class);
            db.queryForList("SELECT security_version,withdrawn_at FROM ap_account WHERE 1=0");
            db.queryForList("SELECT failed_attempts,locked_until FROM ap_email_credential WHERE 1=0");
            db.queryForList("SELECT archived_at FROM ap_business_scope WHERE 1=0");
            db.queryForList("SELECT saved_title,saved_answers_json,saved_assets_json FROM ap_work_draft WHERE 1=0");
            db.queryForList("SELECT version FROM ap_asset WHERE 1=0");
        } catch(org.springframework.dao.DataAccessException incomplete) {
            throw new IllegalStateException("V002 테이블·컬럼 적용이 불완전합니다. HeidiSQL의 V002 실행 오류와 02_VERIFY_P1.sql 결과를 확인해 주세요.",incomplete);
        }
    }
}
