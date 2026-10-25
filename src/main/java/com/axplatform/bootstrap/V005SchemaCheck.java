package com.axplatform.bootstrap;

import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(-7)
public class V005SchemaCheck implements ApplicationRunner {
    private final JdbcTemplate db;
    private final org.springframework.core.env.Environment env;
    private final com.axplatform.storage.ManagedStorage storage;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public V005SchemaCheck(
            JdbcTemplate db,
            org.springframework.core.env.Environment env,
            com.axplatform.storage.ManagedStorage storage,
            org.springframework.transaction.support.TransactionTemplate tx) {
        this.db = db;
        this.env = env;
        this.storage = storage;
        this.tx = tx;
    }

    public void run(ApplicationArguments args) {
        try {
            if (db.queryForObject(
                            "SELECT COUNT(*) FROM ap_schema_migration WHERE version='V005'",
                            Integer.class)
                    != 1) throw new IllegalStateException("V005");
            db.queryForList(
                    "SELECT"
                        + " owner_account_id,scope_kind,workflow,project_id,purpose,root_kind,storage_state,purged_at"
                        + " FROM ap_asset WHERE 1=0");
            for (String table :
                    java.util.List.of(
                            "ap_storage_copy",
                            "ap_usage_policy",
                            "ap_operation_settings",
                            "ap_model_price",
                            "ap_ai_call",
                            "ap_activity_event",
                            "ap_operations_audit"))
                db.queryForList("SELECT * FROM " + table + " WHERE 1=0");
            tx.executeWithoutResult(
                    t -> {
                        var binding =
                                db.queryForMap(
                                        "SELECT * FROM ap_storage_binding WHERE id=1 FOR UPDATE");
                        // DB metadata is shared; file availability is local to each running computer.
                        // Never rebind the shared row on another computer's behalf.
                        if (storage.perDevice()) {
                            org.slf4j.LoggerFactory.getLogger(V005SchemaCheck.class).info(
                                    "PC별 파일 보관 모드: DB 공유 허용. 원본 파일은 현재 컴퓨터에서 확인합니다.");
                            return;
                        }
                        String profile = env.getProperty("ax.storage.environment", "local"),
                                root = storage.roots().get("managedRoot").replace('\\', '/');
                        if (binding.get("environment_key") == null)
                            db.update(
                                    "UPDATE ap_storage_binding SET"
                                        + " environment_key=?,root_path=?,volume_id=?,bound_at=CURRENT_TIMESTAMP"
                                        + " WHERE id=1",
                                    profile,
                                    root,
                                    storage.volumeId());
                        else if (!profile.equals(binding.get("environment_key"))
                                || !root.equals(binding.get("root_path"))
                                || !storage.volumeId().equals(binding.get("volume_id")))
                            throw new IllegalStateException(
                                    "이 DB는 다른 환경/저장 루트에 연결되어 있습니다. dev/test는 각각의 DB와 저장 폴더를 사용하세요."
                                        + " 경로 이전은 V005 적용 안내의 환경 이전 절차를 따르세요.");
                    });
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "V005 스키마 또는 저장 환경을 확인하지 못했습니다. 10번 SQL·11번 검증 SQL과 V005_APPLY의 환경 이전 절차를"
                        + " 확인하세요. 기존 DB·자료 폴더를 삭제하지 마세요.",
                    e);
        }
    }
}
