package com.axplatform.bootstrap;

import java.time.Clock;
import java.time.LocalDateTime;
import com.axplatform.security.AdminCredentialStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Order(0)
public class InitialAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AdminCredentialStore store;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public InitialAdminBootstrap(JdbcTemplate jdbc, AdminCredentialStore store, PasswordEncoder encoder,
            TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.store = store; this.encoder = encoder; this.transactions = transactions; this.clock = clock;
    }
    @Override public void run(ApplicationArguments args) { initialize(); }

    public void initialize() {
        Integer migration = jdbc.queryForObject("SELECT COUNT(*) FROM ap_schema_migration WHERE version='V001'", Integer.class);
        if (migration == null || migration != 1) throw new IllegalStateException("HeidiSQL에서 V001__foundation.sql을 먼저 실행해 주세요.");
        transactions.executeWithoutResult(tx -> {
            var rows = jdbc.queryForList("SELECT completed_at,account_id FROM ap_bootstrap WHERE name='INITIAL_ADMIN' FOR UPDATE");
            if (rows.size() != 1) throw new IllegalStateException("관리자 초기화 잠금 행이 없습니다. V001 SQL을 확인해 주세요.");
            var marker = rows.get(0);
            if (marker.get("completed_at") != null) {
                var existing = store.lockByUsername("admin");
                Object remembered = marker.get("account_id");
                if (existing.isEmpty() || !(remembered instanceof Number number)
                        || existing.get().accountId() != number.longValue()
                        || !"SYSTEM_ADMIN".equals(existing.get().accountType())) {
                    throw new IllegalStateException("초기화 완료 기록과 관리자 계정이 일치하지 않습니다. 기본 암호로 재생성하지 않습니다. 복구 안내를 확인해 주세요.");
                }
                // Existing password, role and status are intentionally preserved.
                return;
            }
            if (store.lockByUsername("admin").isPresent()) {
                throw new IllegalStateException("admin ID가 이미 존재하지만 초기화 기록이 없습니다. 임의 승격/암호 초기화하지 않습니다.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            long account = store.insert("""
                    INSERT INTO ap_account(account_type,system_role,status,display_name,created_at,updated_at,version)
                    VALUES ('SYSTEM_ADMIN','SUPER_ADMIN','ACTIVE','시스템 관리자',?,?,0)
                    """, now, now);
            String initialHash = encoder.encode("12345");
            jdbc.update("""
                    INSERT INTO ap_admin_credential(account_id,username,password_hash,security_version,failed_attempts,created_at,updated_at)
                    VALUES (?,'admin',?,0,0,?,?)
                    """, account, initialHash, now, now);
            long workspace = store.insert("""
                    INSERT INTO ap_workspace(owner_account_id,name,kind,plan_code,created_at,updated_at,version)
                    VALUES (?,'관리자 시험 공간','ADMIN_TEST',NULL,?,?,0)
                    """, account, now, now);
            jdbc.update("INSERT INTO ap_workspace_member(workspace_id,account_id,member_role,created_at) VALUES (?,?,'OWNER',?)",
                    workspace, account, now);
            jdbc.update("""
                    INSERT INTO ap_business_scope(workspace_id,name,is_default,created_at,updated_at,version)
                    VALUES (?,'기본 사업',TRUE,?,?,0)
                    """, workspace, now, now);
            jdbc.update("UPDATE ap_bootstrap SET completed_at=?,account_id=? WHERE name='INITIAL_ADMIN'", now, account);
            store.audit(account, "INITIAL_ADMIN_CREATED", "관리자 및 관리자 시험 공간 최초 생성", now);
        });
    }
}
