package com.axplatform;

import java.time.*;
import java.util.*;
import java.util.regex.*;
import com.axplatform.auth.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.*;
import com.axplatform.identity.*;
import com.axplatform.workspace.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties= {
    "spring.config.location=optional:classpath:/test-empty.yml","spring.datasource.url=jdbc:h2:mem:axplatform_p1_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/test-schema.sql","spring.jpa.hibernate.ddl-auto=validate","ax.auth.registration-enabled=true","ax.auth.otp-secret=TEST_ONLY_012345678901234567890123456789abcdef","ax.storage-root=./target/p1-test-assets"
}
) @AutoConfigureMockMvc @Import(P1IntegrationTest.TestDeliveryConfig.class) class P1IntegrationTest {
    @TestConfiguration static class TestDeliveryConfig {
        @Bean @Primary CaptureDelivery testDelivery() {
            return new CaptureDelivery();
        }
    }
    static class CaptureDelivery implements VerificationDelivery {
        final Map<String,String> content=new HashMap<>();
        public boolean emailReady() {
            return true;
        }
        public boolean smsReady() {
            return true;
        }
        public void email(String target,String subject,String body) {
            content.put(target,body);
        }
        public void sms(String phone,String body) {
            content.put(phone,body);
        }
        String otp(String target) {
            Matcher m=Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(content.get(target));
            if(!m.find())throw new AssertionError("OTP missing");
            return m.group();
        }
    }
    @Autowired JdbcTemplate db;
    @Autowired VerificationService verification;
    @Autowired CustomerAccountService accounts;
    @Autowired RecoveryService recovery;
    @Autowired CaptureDelivery delivery;
    @Autowired PolicyService policies;
    @Autowired MemberAdminService members;
    @Autowired DraftService drafts;
    @Autowired AccessService access;
    @Autowired MockMvc mvc;
    @Autowired JsonCodec json;
    @Autowired AdminAuthenticationService admins;
    @BeforeEach void isolated() {
        String url;
        try(var c=Objects.requireNonNull(db.getDataSource()).getConnection()) {
            url=c.getMetaData().getURL();
        }
        catch(Exception e) {
            throw new AssertionError(e);
        }
        assertThat(url).startsWith("jdbc:h2:mem:axplatform_p1_test");
        SecurityContextHolder.clearContext();
        for(String table:List.of("ap_ai_run", "ap_sheet", "ap_draft_asset","ap_work_draft","ap_asset","ap_password_reset","ap_consent","ap_account_preference","ap_workspace_dashboard","ap_workspace_tier_history","ap_workspace_tier_override","ap_member_change","ap_event_outbox","ap_verification","ap_auth_rate","ap_workspace_member"))db.update("DELETE FROM "+table);
        db.update("DELETE FROM ap_business_scope WHERE workspace_id IN(SELECT id FROM ap_workspace WHERE kind='CUSTOMER')");
        db.update("DELETE FROM ap_workspace WHERE kind='CUSTOMER'");
        db.update("DELETE FROM ap_email_credential");
        db.update("DELETE FROM ap_external_identity");
        db.update("DELETE FROM ap_account WHERE account_type='CUSTOMER'");
        db.update("UPDATE ap_policy_state SET revision=1,active_policy_id=1 WHERE id=1");
        db.update("DELETE FROM ap_policy_request");
        db.update("DELETE FROM ap_policy_grant WHERE policy_version_id<>1");
        db.update("DELETE FROM ap_policy_version WHERE id<>1");
        delivery.content.clear();
    }
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }
    private String proof(String purpose,String target,MockHttpSession session) {
        var receipt=verification.issue(purpose,target,session,"test-ip");
        String normalized=purpose.equals("SIGNUP_EMAIL")?Inputs.email(target):Inputs.phone(target);
        assertThat(verification.verify(receipt.id(),delivery.otp(normalized),session)).isTrue();
        return receipt.id();
    }
    private CustomerPrincipal register(String email,String phone,MockHttpSession session) {
        String ep=proof("SIGNUP_EMAIL",email,session),pp=proof("SIGNUP_PHONE",phone,session);
        return accounts.signup(new CustomerAccountService.Signup(email,"Password-12345","Password-12345","테스트",phone,ep,pp,true,true),session);
    }
    private void administrator() {
        var principal=admins.authenticate("admin","12345").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN")));
    }
    private long workspace(CustomerPrincipal p) {
        return db.queryForObject("SELECT id FROM ap_workspace WHERE owner_account_id=?",Long.class,p.accountId());
    }
    private long business(CustomerPrincipal p) {
        return db.queryForObject("SELECT b.id FROM ap_business_scope b JOIN ap_workspace w ON w.id=b.workspace_id WHERE w.owner_account_id=?",Long.class,p.accountId());
    }
    @Test void otpIsSessionBoundAndFailedAttemptsPersist() {
        var session=new MockHttpSession();
        var receipt=verification.issue("SIGNUP_PHONE","01012345678",session,"test-ip");
        assertThat(verification.verify(receipt.id(),delivery.otp("+821012345678"),new MockHttpSession())).isFalse();
        String correct=delivery.otp("+821012345678"),wrong=correct.equals("000000")?"999999":"000000";
        for(int i=0;
        i<5;
        i++)assertThat(verification.verify(receipt.id(),wrong,session)).isFalse();
        assertThat(db.queryForObject("SELECT attempts FROM ap_verification WHERE id=?",Integer.class,receipt.id())).isEqualTo(5);
        assertThat(verification.verify(receipt.id(),correct,session)).isFalse();
    }
    @Test void signupConsumesProofsOnceAndCanonicalEmailConflictsRollback() {
        var session=new MockHttpSession();
        String ep=proof("SIGNUP_EMAIL","User@Example.com",session),pp=proof("SIGNUP_PHONE","01012345678",session);
        var dto=new CustomerAccountService.Signup("USER@EXAMPLE.COM","Password-12345","Password-12345","테스트","01012345678",ep,pp,true,true);
        accounts.signup(dto,session);
        assertThatThrownBy(()->accounts.signup(dto,session)).isInstanceOf(Problem.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ap_email_credential WHERE login_email='user@example.com'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM ap_verification WHERE id=?",String.class,ep)).isEqualTo("CONSUMED");
    }
    @Test void emailAndSocialWithSameContactRemainSeparate() {
        var email=register("same@example.com","01012345678",new MockHttpSession());
        var socialSession=new MockHttpSession();
        socialSession.setAttribute("SOCIAL_PENDING",new CustomerAccountService.SocialPending("GOOGLE","test-subject","same@example.com",true,"테스트","",Instant.now().plusSeconds(1800)));
        String pp=proof("SIGNUP_PHONE","01012345679",socialSession);
        var social=accounts.signup(new CustomerAccountService.Signup("same@example.com","","","SNS","01012345679","",pp,true,true),socialSession);
        assertThat(social.accountId()).isNotEqualTo(email.accountId());
        assertThat(accounts.authenticate("same@example.com","Password-12345").orElseThrow().accountId()).isEqualTo(email.accountId());
    }
    @Test void changedTargetInvalidatesEarlierVerifiedProof() {
        var session=new MockHttpSession();
        String old=proof("SIGNUP_EMAIL","old@example.com",session);
        verification.issue("SIGNUP_EMAIL","new@example.com",session,"test-ip");
        assertThat(db.queryForObject("SELECT status FROM ap_verification WHERE id=?",String.class,old)).isEqualTo("REPLACED");
        assertThatThrownBy(()->verification.consume(old,"SIGNUP_EMAIL","old@example.com",session)).isInstanceOf(Problem.class);
    }
    @Test void currentSessionsLoseRemovedPermissionAndOtherTiersStayUnchanged() {
        var p=register("remove@example.com","01012345678",new MockHttpSession());
        long w=workspace(p);
        assertThat(access.allowed(p,w)).contains("workspace.scope.create");
        administrator();
        var snapshot=policies.snapshot();
        var grants=policies.grants(((Number)snapshot.get("versionId")).longValue());
        var untouched=List.copyOf(grants.get("TIER1"));
        grants.get("GUEST").remove("workspace.scope.create");
        String key=UUID.randomUUID().toString();
        var dto=new PolicyService.Draft(((Number)snapshot.get("revision")).longValue(),grants,"test revocation",key,null);
        var first=policies.apply(dto);
        var retry=policies.apply(dto);
        assertThat(retry.get("id")).isEqualTo(first.get("id"));
        assertThat(access.allowed(p,w)).doesNotContain("workspace.scope.create");
        assertThat(policies.grants(((Number)first.get("id")).longValue()).get("TIER1")).isEqualTo(untouched);
        var altered=new PolicyService.Draft(dto.baseRevision(),grants,"different payload",key,null);
        assertThatThrownBy(()->policies.apply(altered)).isInstanceOf(Problem.class);
    }
    @Test void tenantBoundaryAndOptimisticDraftVersionAreEnforced() {
        var a=register("a@example.com","01012345678",new MockHttpSession());
        var b=register("b@example.com","01012345679",new MockHttpSession());
        long scope=business(a);
        assertThatThrownBy(()->drafts.list(b,scope,false)).isInstanceOf(Problem.class);
        var c=drafts.create(a,new DraftService.Create(scope,"EXCEL","월간 매출"));
        String id=(String)c.get("id");
        drafts.edit(a,id,new DraftService.Edit("매출",Map.of("purpose","비교"),List.of(),0));
        assertThatThrownBy(()->drafts.edit(a,id,new DraftService.Edit("stale",Map.of(),List.of(),0))).isInstanceOf(Problem.class);
        assertThat(drafts.list(a,scope,false).get(0)).doesNotContainKeys("answers","answers_json","assetIds","disk_key");
    }
    @Test void partialCancelPreservesOtherEditedFields() {
        var a=register("cancel@example.com","01012345678",new MockHttpSession());
        String id=(String)drafts.create(a,new DraftService.Create(business(a),"WORD","문서")).get("id");
        drafts.edit(a,id,new DraftService.Edit("문서",Map.of("purpose","원래 목적","details","원래 조건"),List.of(),0));
        drafts.command(a,id,"save",new DraftService.Command(1,null));
        drafts.edit(a,id,new DraftService.Edit("수정 이름",Map.of("purpose","새 목적","details","새 조건"),List.of(),2));
        var result=drafts.command(a,id,"cancel",new DraftService.Command(3,List.of("purpose")));
        var answerMap=(Map<?,?>)result.get("answers");
        assertThat(answerMap.get("purpose")).isEqualTo("원래 목적");
        assertThat(answerMap.get("details")).isEqualTo("새 조건");
        assertThat(result.get("title")).isEqualTo("수정 이름");
    }
    @Test void suspensionRevokesAnExistingPrincipalAndLastAdminCannotBeSuspended() {
        var p=register("status@example.com","01012345678",new MockHttpSession());
        administrator();
        members.change(p.accountId(),new MemberAdminService.Change("테스트","status@example.com","01012345678","SUSPENDED",0,"test stop"));
        assertThatThrownBy(()->access.allowed(p,workspace(p))).isInstanceOf(Problem.class);
        long adminId=Subjects.current().accountId();
        assertThatThrownBy(()->members.change(adminId,new MemberAdminService.Change("관리자",null,null,"SUSPENDED",0,"test last"))).isInstanceOf(Problem.class);
    }
    @Test void passwordResetTokenIsHashedSingleUseAndRevokesOldPrincipal() {
        var p=register("reset@example.com","01012345678",new MockHttpSession());
        recovery.requestReset("reset@example.com","01012345678","test-ip");
        String body=delivery.content.get("reset@example.com");
        Matcher m=Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(body);
        assertThat(m.find()).isTrue();
        String token=m.group(1);
        assertThat(db.queryForObject("SELECT token_hash FROM ap_password_reset WHERE account_id=?",String.class,p.accountId())).isEqualTo(Crypto.sha256(token));
        recovery.reset(token,"NewPassword-456","NewPassword-456");
        assertThatThrownBy(()->recovery.reset(token,"OtherPassword-456","OtherPassword-456")).isInstanceOf(Problem.class);
        assertThatThrownBy(()->access.allowed(p,workspace(p))).isInstanceOf(Problem.class);
        assertThat(accounts.authenticate("reset@example.com","Password-12345")).isEmpty();
    }
    @Test void mutationApiRequiresCsrfAndPreparedFeaturesCannotBeActivatedByCatalogOnly()throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        administrator();
        var snapshot=policies.snapshot();
        var grants=policies.grants(((Number)snapshot.get("versionId")).longValue());
        grants.get("GUEST").add("excel.file.upload");
        assertThatThrownBy(()->policies.preview(new PolicyService.Draft(((Number)snapshot.get("revision")).longValue(),grants,"future",UUID.randomUUID().toString(),null))).isInstanceOf(Problem.class);
    }
}
