package com.axplatform.bootstrap;

import java.util.List;
import com.axplatform.policy.ActionRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fail before bootstrap/normal use when the required additive migration is incomplete. */
@Component
@Order(-9)
public class P3SchemaCheck implements ApplicationRunner {
    private final JdbcTemplate db;
    private final ActionRegistry registry;
    private static final List<String> ACTIONS = List.of(
        "workspace.ai.run",
        "workspace.ai.read",
        "workspace.ai.apply",
        "workspace.ai.cancel",
        "workspace.sheet.read",
        "workspace.sheet.update",
        "workspace.sheet.export",
        "workspace.sheet.schema.update",
        "workspace.sheet.rows.create",
        "workspace.sheet.rows.delete",
        "workspace.sheet.cells.update",
        "workspace.sheet.export.template");


    public P3SchemaCheck(JdbcTemplate db, ActionRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            Integer marked = db.queryForObject(
                "SELECT COUNT(*) FROM ap_schema_migration WHERE version='V003'", Integer.class);
            if (marked == null || marked != 1) {
                throw new IllegalStateException(
                    "V003 적용 기록이 없습니다. 앱을 중지하고 HeidiSQL에서 06_V003__ai_and_sheet.sql 전체를 실행한 다음 07_VERIFY_V003.sql로 확인해 주세요. 기존 DB를 삭제하지 마세요.");
            }
            // Column names are intentionally fixed in code. No user-supplied SQL is executed.
            db.queryForList("SELECT id,account_id,workspace_id,business_scope_id,draft_id,request_key,request_hash,base_draft_version,base_sheet_version,request_json,result_json,status,phase,model,reasoning_effort,input_tokens,output_tokens,safe_error_code,safe_message,apply_key,apply_hash,applied_json,worker_owner,lease_until,created_at,updated_at FROM ap_ai_run WHERE 1=0");
            db.queryForList("SELECT draft_id,workspace_id,business_scope_id,content_json,previous_json,version,created_at,updated_at FROM ap_sheet WHERE 1=0");
            for (String expected : ACTIONS) {
                if (!registry.implemented(expected, expected)) {
                    throw new IllegalStateException("V003 서버 행동 등록이 누락되었습니다: " + expected);
                }
                var rows = db.queryForList(
                    "SELECT code,binding_key FROM ap_permission_action WHERE code=?", expected);
                // An unused unbound draft may be removed; all runtime actions then remain denied.
                if (rows.isEmpty()) continue;
                var row = rows.get(0);
                Object binding = row.get("binding_key");
                if (!expected.equals(row.get("code")) || binding != null && !expected.equals(binding)) {
                    throw new IllegalStateException(
                        "V003 행동 연결의 의미가 다릅니다. 07_VERIFY_V003.sql 결과를 확인해 주세요: " + expected);
                }
                // PREPARING/DISABLED/ARCHIVED/adminOnly and unbound definitions are legitimate.
                if (binding == null) continue;
                for (String dependency : registry.requiredDependencies(expected)) {
                    Integer count = db.queryForObject(
                        "SELECT COUNT(*) FROM ap_permission_dependency WHERE action_code=? AND depends_on=?",
                        Integer.class, expected, dependency);
                    if (count == null || count != 1) {
                        throw new IllegalStateException(
                            "V003 필수 의존 권한이 누락되었습니다. 07_VERIFY_V003.sql 결과를 확인해 주세요: "
                            + expected + " -> " + dependency);
                    }
                }
            }
        } catch (DataAccessException incomplete) {
            throw new IllegalStateException(
                "V003 테이블·컬럼·행동 등록이 불완전합니다. HeidiSQL에서 첫 SQL 오류와 07_VERIFY_V003.sql 결과를 확인해 주세요. 기존 데이터와 개인 설정을 삭제하지 마세요.",
                incomplete);
        }
    }
}
