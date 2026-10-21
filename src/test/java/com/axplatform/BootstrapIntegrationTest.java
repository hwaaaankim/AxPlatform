package com.axplatform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.axplatform.bootstrap.InitialAdminBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** H2 checks transaction/auth behavior; real MariaDB and Eclipse checks are separate. */
@SpringBootTest(properties = {
    "spring.config.location=optional:classpath:/test-empty.yml",     "spring.datasource.url=jdbc:h2:mem:axplatform_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",     "spring.datasource.username=sa", "spring.datasource.password=",     "spring.datasource.driver-class-name=org.h2.Driver",     "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/test-schema.sql",     "spring.jpa.hibernate.ddl-auto=validate"
}
) @AutoConfigureMockMvc class BootstrapIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialAdminBootstrap bootstrap;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @BeforeEach void freshTestData() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
        // This cleanup is guarded to an in-memory test DB only; never to the personal MariaDB.
        for (String table : new String[] {
            "ap_ai_run", "ap_sheet", "ap_draft_asset", "ap_work_draft", "ap_asset", "ap_workspace_dashboard", "ap_workspace_tier_override", "ap_workspace_tier_history", "ap_password_reset", "ap_consent", "ap_account_preference", "ap_event_outbox", "ap_admin_audit", "ap_business_scope", "ap_workspace_member",                 "ap_workspace", "ap_admin_credential", "ap_email_credential", "ap_external_identity", "ap_account", "ap_bootstrap"
        }
        ) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO ap_bootstrap(name) VALUES ('INITIAL_ADMIN')");
        bootstrap.initialize();
    }
    @Test void storesOneHashAndKeepsChangedPasswordOnRestart() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM ap_admin_credential WHERE username='admin'", String.class);
        assertThat(hash).isNotEqualTo("12345");
        assertThat(encoder.matches("12345", hash)).isTrue();
        String changed = encoder.encode("changed-admin-pass-2026");
        jdbc.update("UPDATE ap_admin_credential SET password_hash=?,security_version=9,failed_attempts=3", changed);
        bootstrap.initialize();
        bootstrap.initialize();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM ap_admin_credential", String.class)).isEqualTo(changed);
        assertThat(jdbc.queryForObject("SELECT security_version FROM ap_admin_credential", Long.class)).isEqualTo(9L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ap_admin_credential", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ap_workspace", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentStartsDoNotCreateExtraAdministrators() throws Exception {
        var one = CompletableFuture.runAsync(bootstrap::initialize);
        var two = CompletableFuture.runAsync(bootstrap::initialize);
        CompletableFuture.allOf(one, two).get(10, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ap_admin_credential", Integer.class)).isEqualTo(1);
    }
    @Test void missingAdministratorDoesNotRegenerateDefaultPassword() {
        jdbc.update("DELETE FROM ap_admin_credential");
        assertThatThrownBy(bootstrap::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ap_admin_credential", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ap_bootstrap WHERE completed_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }
    @Test void collisionWithoutMarkerDoesNotResetCredentials() {
        String before = jdbc.queryForObject("SELECT password_hash FROM ap_admin_credential", String.class);
        jdbc.update("UPDATE ap_bootstrap SET completed_at=NULL,account_id=NULL");
        assertThatThrownBy(bootstrap::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM ap_admin_credential", String.class)).isEqualTo(before);
    }
    @Test void unauthenticatedApiIsJsonAndLoginRequiresCsrf() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())             .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(post("/admin/login").param("username", "admin").param("password", "12345"))             .andExpect(status().isForbidden());
    }
    @Test void administratorLoginRendersRealThymeleafPage() throws Exception {
        MockHttpSession session = login("12345");
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk())             .andExpect(content().string(org.hamcrest.Matchers.containsString("관리 홈")))             .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("th:replace"))));
    }
    @Test void failedAttemptsCommitAndLockTheCredential() throws Exception {
        for (int i=0;
        i<5;
        i++) mvc.perform(post("/admin/login").with(csrf())             .param("username", "admin").param("password", "wrong-password"))             .andExpect(redirectedUrl("/admin/login?error"));
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM ap_admin_credential", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM ap_admin_credential", java.sql.Timestamp.class)).isNotNull();
        mvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "12345"))             .andExpect(redirectedUrl("/admin/login?error"));
    }
    @Test void passwordChangeRevokesAnotherExistingSession() throws Exception {
        MockHttpSession first = login("12345");
        MockHttpSession second = login("12345");
        mvc.perform(post("/admin/password").session(first).with(csrf())             .param("currentPassword", "12345").param("newPassword", "new-admin-pass-2026")             .param("confirmation", "new-admin-pass-2026").param("securityVersion", "0"))             .andExpect(redirectedUrl("/admin/login?passwordChanged"));
        mvc.perform(get("/api/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "12345"))             .andExpect(redirectedUrl("/admin/login?error"));
        login("new-admin-pass-2026");
    }
    private MockHttpSession login(String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login").with(csrf())             .param("username", "admin").param("password", password))             .andExpect(redirectedUrl("/admin")).andReturn().getRequest().getSession(false);
    }
}
