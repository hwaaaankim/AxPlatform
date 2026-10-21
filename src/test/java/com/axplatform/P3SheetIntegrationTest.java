package com.axplatform;

import com.axplatform.ai.AiService;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.sheet.*;
import com.axplatform.workspace.DraftService;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.config.location=optional:classpath:/test-empty.yml",
    "spring.datasource.url=jdbc:h2:mem:axplatform_p3_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/test-schema.sql",
    "spring.jpa.hibernate.ddl-auto=validate", "ax.integrations.openai.enabled=false", "ax.storage-root=./target/p3-test-assets"
})
@AutoConfigureMockMvc
class P3SheetIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired SheetService sheets;
    @Autowired SheetViewService views;
    @Autowired DraftService drafts;
    @Autowired AiService ai;
    @Autowired com.axplatform.policy.AccessService access;
    @Autowired com.axplatform.workspace.AssetService assets;
    @Autowired AccountSessionService sessions;
    @Autowired com.axplatform.operations.AiMeteringService metering;
    @Autowired java.time.Clock clock;
    @Autowired JsonCodec json;

    record Owner(CustomerPrincipal principal, long workspace, long business) { }
    @BeforeEach void restrictToMemoryDatabase() throws Exception {
        try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:axplatform_p3_test");
        }
        SecurityContextHolder.clearContext();
        for (String table : List.of("ap_ai_call", "ap_sheet_view", "ap_ai_run", "ap_sheet", "ap_draft_asset", "ap_asset", "ap_work_draft", "ap_event_outbox", "ap_usage_policy")) db.update("DELETE FROM " + table);
        db.update("DELETE FROM ap_workspace_member WHERE workspace_id IN (SELECT id FROM ap_workspace WHERE kind='CUSTOMER')");
        db.update("DELETE FROM ap_business_scope WHERE workspace_id IN (SELECT id FROM ap_workspace WHERE kind='CUSTOMER')");
        db.update("DELETE FROM ap_workspace WHERE kind='CUSTOMER'");
        db.update("DELETE FROM ap_email_credential");
        db.update("DELETE FROM ap_external_identity");
        db.update("DELETE FROM ap_account WHERE account_type='CUSTOMER'");
        db.update("DELETE FROM ap_policy_grant WHERE action_code LIKE 'workspace.ai.%' OR action_code LIKE 'workspace.sheet.%'");
    }
    @AfterEach void clearSessionContext() { SecurityContextHolder.clearContext(); }
    private Owner customer() {
        String suffix = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        String email = "p3-" + suffix + "@example.invalid";
        db.update("INSERT INTO ap_account(account_type,system_role,status,display_name,contact_email,phone_e164,created_at,updated_at) VALUES('CUSTOMER','CUSTOMER','ACTIVE','P3 test',?,'+821012345678',?,?)", email, now, now);
        long id = db.queryForObject("SELECT id FROM ap_account WHERE contact_email=?", Long.class, email);
        db.update("INSERT INTO ap_email_credential(account_id,login_email,password_hash,email_verified_at) VALUES(?,?,?,?)", id, email, "TEST_ONLY_NOT_A_PASSWORD_HASH", now);
        db.update("INSERT INTO ap_workspace(owner_account_id,name,kind,plan_code,created_at,updated_at) VALUES(?,?,'CUSTOMER','FREE',?,?)", id, suffix, now, now);
        long workspace = db.queryForObject("SELECT id FROM ap_workspace WHERE owner_account_id=?", Long.class, id);
        db.update("INSERT INTO ap_workspace_member(workspace_id,account_id,member_role,created_at) VALUES(?,?,'OWNER',?)", workspace, id, now);
        db.update("INSERT INTO ap_business_scope(workspace_id,name,is_default,created_at,updated_at) VALUES(?,'P3 scope',TRUE,?,?)", workspace, now, now);
        long business = db.queryForObject("SELECT id FROM ap_business_scope WHERE workspace_id=?", Long.class, workspace);
        return new Owner(new CustomerPrincipal(id, 0, 0, "EMAIL", "P3 test"), workspace, business);
    }
    private void grant(String... actions) {
        Long policy = db.queryForObject("SELECT active_policy_id FROM ap_policy_state WHERE id=1", Long.class);
        for (String action : actions)
            db.update("INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT ?,'GUEST',?,999 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=? AND tier_code='GUEST' AND action_code=?)", policy, action, policy, action);
    }
    private void ordinarySheetPermissions() {
        grant("workspace.sheet.read", "workspace.sheet.update", "workspace.sheet.schema.update", "workspace.sheet.rows.create", "workspace.sheet.cells.update");
    }
    private Map<String,Object> table(String value, String row) {
        return Map.of("columns", List.of(Map.of("id", "code", "label", "식별번호", "type", "TEXT", "unit", "", "width", 160)), "rows", List.of(List.of(value)), "rowIds", List.of(row));
    }
    private String draft(Owner owner, String workflow) {
        return (String) drafts.create(owner.principal(), new DraftService.Create(owner.business(), workflow, "원래 제목")).get("id");
    }
    private MockHttpSession session(CustomerPrincipal principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, "", principal.authorities()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
    private Map<String,Object> proposal(Map<String,Object> sheet) {
        var proposal = new LinkedHashMap<String,Object>();
        proposal.put("summary", "사용자가 검토할 제안"); proposal.put("confirmed", List.of()); proposal.put("assumptions", List.of()); proposal.put("questions", List.of());
        proposal.put("draft", Map.of("title", "제안 제목", "answers", Map.of("purpose", "검토 목적", "data", "", "result", "", "details", "", "audience", "")));
        proposal.put("sheet", sheet); proposal.put("warnings", List.of()); proposal.put("ready", true); return proposal;
    }
    private String readyRun(Owner owner, String draft, long draftVersion, long sheetVersion, Map<String,Object> proposal, MockHttpSession session) {
        String id = UUID.randomUUID().toString(); var now = LocalDateTime.now();
        db.update("INSERT INTO ap_ai_run(id,account_id,workspace_id,business_scope_id,draft_id,request_key,request_hash,base_draft_version,base_sheet_version,request_json,result_json,status,phase,model,reasoning_effort,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'READY','test ready','TEST_ONLY_MODEL','high',?,?)",
            id, owner.principal().accountId(), owner.workspace(), owner.business(), draft, UUID.randomUUID().toString(), "a".repeat(64), draftVersion, sheetVersion,
            json.write(Map.of("prompt", "테스트 요청", "assetIds", List.of(), "originSessionHash", Crypto.sha256(session.getId()))), json.write(Map.of("proposal", proposal, "toolNames", List.of("propose_workflow"))), now, now);
        return id;
    }

    @Test void dashboardAndGuidedDraftRenderAsRealThymeleafWithScopedSession() throws Exception {
        Owner owner = customer(); String id = draft(owner, "EXCEL"); MockHttpSession session = session(owner.principal());
        mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("dashboard-root")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("th:replace"))));
        mvc.perform(get("/work/drafts/" + id).session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("guided-ai")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("sheet")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("th:attr"))));
    }
    @Test void newActionsAreDeniedUntilExplicitlyAssignedAndTenantScopeIsChecked() {
        Owner owner = customer(); String id = draft(owner, "EXCEL");
        assertThatThrownBy(() -> sheets.get(owner.principal(), id)).isInstanceOf(Problem.class);
        ordinarySheetPermissions();
        assertThat(((Number)sheets.get(owner.principal(), id).get("version")).longValue()).isEqualTo(-1);
        assertThatThrownBy(() -> sheets.get(customer().principal(), id)).isInstanceOf(Problem.class);
        String word = draft(owner, "WORD");
        assertThatThrownBy(() -> sheets.put(owner.principal(), word, table("001", "r1"), -1, 0)).isInstanceOf(Problem.class);
    }
    @Test void bothVersionsAndRealRowDiffPermissionsAreEnforcedWithoutPartialWrites() {
        ordinarySheetPermissions(); Owner owner = customer(); String id = draft(owner, "EXCEL");
        var first = sheets.put(owner.principal(), id, table("001", "r1"), -1, 0);
        assertThat(((Number)first.get("version")).longValue()).isZero();
        assertThat(((Number)first.get("draftVersion")).longValue()).isEqualTo(1);
        assertThatThrownBy(() -> sheets.put(owner.principal(), id, table("002", "r1"), -1, 1)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> sheets.put(owner.principal(), id, table("002", "r1"), 0, 0)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> sheets.put(owner.principal(), id, table("002", "new_row"), 0, 1)).isInstanceOf(Problem.class);
        assertThat(sheets.get(owner.principal(), id).get("sheet")).isEqualTo(table("001", "r1"));
        assertThat(db.queryForObject("SELECT version FROM ap_work_draft WHERE id=?", Long.class, id)).isEqualTo(1);
        assertThat(sheets.put(owner.principal(), id, table("002", "r1"), 0, 1).get("sheet")).isEqualTo(table("002", "r1"));
    }
    @Test void undoUsesCurrentPermissionsVersionsAndOnlyOneSnapshot() {
        ordinarySheetPermissions(); Owner owner = customer(); String id = draft(owner, "EXCEL");
        sheets.put(owner.principal(), id, table("001", "r1"), -1, 0);
        sheets.put(owner.principal(), id, table("002", "r1"), 0, 1);
        var undone = sheets.undo(owner.principal(), id, 1, 2);
        assertThat(undone.get("sheet")).isEqualTo(table("001", "r1")); assertThat(undone.get("canUndo")).isEqualTo(false);
        assertThatThrownBy(() -> sheets.undo(owner.principal(), id, 2, 3)).isInstanceOf(Problem.class);
    }
    @Test void aiApplyRollsBackDraftWhenAnyActualSheetPermissionIsMissingAndIsIdempotent() {
        ordinarySheetPermissions(); grant("workspace.ai.read", "workspace.ai.apply");
        Owner owner = customer(); String id = draft(owner, "EXCEL");
        sheets.put(owner.principal(), id, table("001", "r1"), -1, 0);
        var proposed = proposal(table("002", "r_replaced"));
        var session = session(owner.principal());
        String run = readyRun(owner, id, 1, 0, proposed, session), key = UUID.randomUUID().toString();
        var command = new AiService.Apply(key, 1, 0, List.of("title", "sheet"), proposed, true);
        assertThatThrownBy(() -> ai.apply(owner.principal(), session, run, command, key)).isInstanceOf(Problem.class);
        assertThat(drafts.get(owner.principal(), id).get("title")).isEqualTo("원래 제목");
        assertThat(db.queryForObject("SELECT status FROM ap_ai_run WHERE id=?", String.class, run)).isEqualTo("READY");
        assertThat(sheets.get(owner.principal(), id).get("sheet")).isEqualTo(table("001", "r1"));
        grant("workspace.sheet.rows.delete");
        var applied = ai.apply(owner.principal(), session, run, command, key);
        assertThat(applied.get("status")).isEqualTo("APPLIED");
        assertThat(drafts.get(owner.principal(), id).get("title")).isEqualTo("제안 제목");
        assertThat(ai.apply(owner.principal(), session, run, command, key).get("applied")).isEqualTo(applied.get("applied"));
        assertThat(db.queryForObject("SELECT version FROM ap_work_draft WHERE id=?", Long.class, id)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT version FROM ap_sheet WHERE draft_id=?", Long.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> ai.apply(owner.principal(), session, run, new AiService.Apply(UUID.randomUUID().toString(), 1, 0, command.fields(), proposed, true), key)).isInstanceOf(Problem.class);
    }
    @Test void cancelledProposalCannotApplyAndCancellingNeverModifiesTheDraft() {
        grant("workspace.ai.read", "workspace.ai.cancel", "workspace.ai.apply");
        Owner owner = customer(); String id = draft(owner, "WORD");
        MockHttpSession session = session(owner.principal());
        String run = readyRun(owner, id, 0, -1, proposal(null), session);
        assertThat(ai.cancel(owner.principal(), session, run).get("status")).isEqualTo("CANCELLED");
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> ai.apply(owner.principal(), session, run, new AiService.Apply(key, 0, -1, List.of("title"), null, true), key)).isInstanceOf(Problem.class);
        assertThat(drafts.get(owner.principal(), id).get("title")).isEqualTo("원래 제목");
        assertThat(db.queryForObject("SELECT version FROM ap_work_draft WHERE id=?", Long.class, id)).isZero();
        assertThatThrownBy(() -> ai.get(customer().principal(), run)).isInstanceOf(Problem.class);
    }
    @Test void templateExportOmitsRowsAndRequiresItsOwnPermission() throws Exception {
        ordinarySheetPermissions(); Owner owner = customer(); String id = draft(owner, "EXCEL");
        sheets.put(owner.principal(), id, table("PRIVATE_ROW", "r1"), -1, 0);
        grant("workspace.sheet.export");
        assertThatThrownBy(() -> sheets.export(owner.principal(), id, true)).isInstanceOf(Problem.class);
        grant("workspace.sheet.export.template");
        var data = sheets.export(owner.principal(), id, true).data();
        StringBuilder parts = new StringBuilder();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null;) parts.append(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(parts.toString()).contains("식별번호").doesNotContain("PRIVATE_ROW");
        assertThat(sheets.get(owner.principal(), id).get("sheet")).isEqualTo(table("PRIVATE_ROW", "r1"));
    }
    @Test void savedAnalysisEnforcesTenantPermissionsSheetVersionAndViewVersion() {
        ordinarySheetPermissions();Owner owner=customer();String id=draft(owner,"EXCEL");
        sheets.put(owner.principal(),id,table("A","r1"),-1,0);
        var config=Map.<String,Object>of("name","거래처 수","operation","COUNT","groupColumn","code","valueColumn","");
        assertThatThrownBy(()->views.analyze(owner.principal(),id,config)).isInstanceOf(Problem.class);
        grant("workspace.sheet.analysis.read","workspace.sheet.analysis.manage");
        var result=(Map<?,?>)views.analyze(owner.principal(),id,config);
        assertThat(result.get("rowCount")).isEqualTo(1);
        assertThatThrownBy(()->views.save(owner.principal(),id,"",-1,99,config)).isInstanceOf(Problem.class);
        String createId=UUID.randomUUID().toString();
        var saved=(Map<?,?>)views.save(owner.principal(),id,createId,-1,0,config);String viewId=(String)saved.get("id");
        views.save(owner.principal(),id,createId,-1,0,config);
        assertThat((List<?>)views.list(owner.principal(),id)).hasSize(1);
        assertThatThrownBy(()->views.list(customer().principal(),id)).isInstanceOf(Problem.class);
        assertThatThrownBy(()->views.delete(owner.principal(),id,viewId,99)).isInstanceOf(Problem.class);
        views.delete(owner.principal(),id,viewId,0);
        assertThat((List<?>)views.list(owner.principal(),id)).isEmpty();
        assertThat(sheets.get(owner.principal(),id).get("sheet")).isEqualTo(table("A","r1"));
    }
    @Test void failedRunDiagnosticsReloadForOwnerAndAdminDiagnosticRejectsMembers() throws Exception {
        grant("workspace.ai.read");Owner owner=customer();String draft=draft(owner,"EXCEL");
        MockHttpSession session=session(owner.principal());
        String run=readyRun(owner,draft,0,-1,proposal(null),session);
        var diagnostic=Map.of("httpStatus",401,"providerCode","invalid_api_key","generationRequests",0,"stage","INPUT_TOKENS");
        db.update("UPDATE ap_ai_run SET status='FAILED',safe_error_code='AI_PROVIDER_INVALID_KEY',result_json=? WHERE id=?",
                json.write(Map.of("diagnostic",diagnostic)),run);
        assertThat(ai.get(owner.principal(),run)).containsEntry("diagnostic",diagnostic).containsEntry("errorCode","AI_PROVIDER_INVALID_KEY");
        assertThatThrownBy(()->ai.get(customer().principal(),run)).isInstanceOf(Problem.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/ai/diagnostics")
                .session(session).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void addedFormulaRequiresOwnPermissionAndCannotBypassThroughSheetApply() {
        ordinarySheetPermissions();Owner owner=customer();String id=draft(owner,"EXCEL");
        var c=new LinkedHashMap<String,Object>(Map.of("id","total","label","계산","type","NUMBER","unit","원","width",160,"formula","2*3"));
        var proposed=Map.<String,Object>of("columns",List.of(c),"rows",List.of(List.of("999")),"rowIds",List.of("r1"));
        assertThatThrownBy(()->sheets.put(owner.principal(),id,proposed,-1,0)).isInstanceOf(Problem.class);
        grant("workspace.sheet.formula.update");
        var applied=sheets.put(owner.principal(),id,proposed,-1,0);
        assertThat(((Map<?,?>)applied.get("sheet")).get("rows")).isEqualTo(List.of(List.of("6")));
    }


    private Map<String,Object> workbook(String a,String b){return WorkbookRules.normalize(Map.of("format","AX_WORKBOOK_1","activeSheetId","sheet1","sheets",List.of(
        Map.of("id","sheet1","name","매출","data",table(a,"r1"),"sourceAssetIds",List.of()),
        Map.of("id","inventory","name","재고","data",table(b,"r2"),"sourceAssetIds",List.of()))));}
    @SuppressWarnings("unchecked") private Map<String,Object> storedBook(Owner owner,String id){return (Map<String,Object>)sheets.get(owner.principal(),id).get("workbook");}
    @Test void workbookUpgradeSaveUndoAndTenantChecksKeepAllSheets(){
        ordinarySheetPermissions();grant("workspace.sheet.rows.delete");Owner owner=customer();String id=draft(owner,"EXCEL");
        sheets.put(owner.principal(),id,table("OLD","r1"),-1,0);
        // Simulate a pre-0.6 persisted single table, not an already-normalized workbook.
        db.update("UPDATE ap_sheet SET content_json=? WHERE draft_id=?",json.write(table("OLD","r1")),id);
        assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("OLD","r1"));
        sheets.putWorkbook(owner.principal(),id,workbook("OLD","002"),0,1);
        assertThatThrownBy(()->sheets.put(owner.principal(),id,table("WRONG","r1"),1,2)).isInstanceOf(Problem.class);
        assertThatThrownBy(()->sheets.putWorkbook(customer().principal(),id,workbook("LEAK","002"),1,2)).isInstanceOf(Problem.class);
        assertThatThrownBy(()->sheets.putWorkbook(owner.principal(),id,workbook("STALE","002"),0,1)).isInstanceOf(Problem.class);
        assertThat(WorkbookRules.data(storedBook(owner,id),"inventory")).isEqualTo(table("002","r2"));
        sheets.undo(owner.principal(),id,1,2);assertThat(WorkbookRules.sheets(storedBook(owner,id))).hasSize(1);assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("OLD","r1"));
    }
    @Test void singleAiProposalTargetsOnlyTheRequestedSheet(){
        ordinarySheetPermissions();grant("workspace.ai.read","workspace.ai.apply");Owner owner=customer();String id=draft(owner,"EXCEL");sheets.putWorkbook(owner.principal(),id,workbook("KEEP","OLD"),-1,0);
        var session=session(owner.principal());var proposed=proposal(table("NEW","r2"));String run=readyRun(owner,id,1,0,proposed,session),key=UUID.randomUUID().toString();
        var request=new LinkedHashMap<>(json.object(db.queryForObject("SELECT request_json FROM ap_ai_run WHERE id=?",String.class,run)));request.put("targetSheetId","inventory");request.put("targetSheetName","재고");db.update("UPDATE ap_ai_run SET request_json=? WHERE id=?",json.write(request),run);
        ai.apply(owner.principal(),session,run,new AiService.Apply(key,1,0,List.of("sheet"),proposed,true),key);
        assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("KEEP","r1"));assertThat(WorkbookRules.data(storedBook(owner,id),"inventory")).isEqualTo(table("NEW","r2"));
    }
    @Test void photoBatchAppliesSelectedReadySheetsAtomicallyAndIdempotently(){
        ordinarySheetPermissions();grant("workspace.ai.read","workspace.ai.apply");Owner owner=customer();String id=draft(owner,"EXCEL");sheets.putWorkbook(owner.principal(),id,workbook("A","B"),-1,0);
        var session=session(owner.principal());String run=readyRun(owner,id,1,0,proposal(null),session);
        db.update("UPDATE ap_ai_run SET result_json=? WHERE id=?",json.write(Map.of("sheetResults",List.of(
            Map.of("sheetId","sheet1","sheetName","매출","status","READY","proposal",proposal(table("CHANGED","replacement"))),
            Map.of("sheetId","inventory","sheetName","재고","status","FAILED","message","흐린 사진")))),run);
        String key=UUID.randomUUID().toString();var command=new AiService.BatchApply(key,1,0,List.of("sheet1"),true);
        assertThatThrownBy(()->ai.applyBatch(owner.principal(),session,run,command,key)).isInstanceOf(Problem.class);
        assertThat(drafts.get(owner.principal(),id).get("version")).isEqualTo(1L);assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("A","r1"));
        grant("workspace.sheet.rows.delete");
        String invalidKey=UUID.randomUUID().toString();assertThatThrownBy(()->ai.applyBatch(owner.principal(),session,run,new AiService.BatchApply(invalidKey,1,0,List.of("sheet1","inventory"),true),invalidKey)).isInstanceOf(Problem.class);
        assertThat(drafts.get(owner.principal(),id).get("version")).isEqualTo(1L);
        assertThat(ai.applyBatch(owner.principal(),session,run,command,key).get("status")).isEqualTo("APPLIED");
        assertThat(ai.applyBatch(owner.principal(),session,run,command,key).get("status")).isEqualTo("APPLIED");
        assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("CHANGED","replacement"));assertThat(WorkbookRules.data(storedBook(owner,id),"inventory")).isEqualTo(table("B","r2"));assertThat(drafts.get(owner.principal(),id).get("version")).isEqualTo(2L);
    }
    @Test void analysisUsesSavedSheetIdEvenWhenAnotherTabIsActive(){
        ordinarySheetPermissions();grant("workspace.sheet.analysis.read","workspace.sheet.analysis.manage");Owner owner=customer();String id=draft(owner,"EXCEL");sheets.putWorkbook(owner.principal(),id,workbook("SALES","STOCK"),-1,0);
        var config=Map.<String,Object>of("sheetId","inventory","name","재고 보기","operation","COUNT","groupColumn","code","valueColumn","");
        var result=(Map<?,?>)views.analyze(owner.principal(),id,config);assertThat(result.get("points").toString()).contains("STOCK").doesNotContain("SALES");
        views.save(owner.principal(),id,UUID.randomUUID().toString(),-1,0,config);assertThat(views.list(owner.principal(),id).toString()).contains("inventory");
    }

    @Test void photoWorkerUsesOneSourcePerSheetAndMetersEachCallWithoutOverwritingData() throws Exception {
        ordinarySheetPermissions();grant("workspace.ai.read","workspace.ai.run","workspace.ai.apply","workspace.asset.upload","workspace.asset.list","workspace.asset.download");
        Owner owner=customer();String id=draft(owner,"EXCEL");var session=session(owner.principal());
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        List<String> sourceIds=new ArrayList<>();for(int i=0;i<2;i++)sourceIds.add((String)assets.store(owner.principal(),owner.business(),"사진"+i+".png",out.toByteArray(),"PROJECT","EXCEL",id,"PHOTO").get("id"));
        drafts.edit(owner.principal(),id,new DraftService.Edit("원래 제목",Map.of(),sourceIds,0));
        var original=workbook("KEEP-A","KEEP-B");var entries=new ArrayList<Map<String,Object>>();int index=0;for(var entry:WorkbookRules.sheets(original)){var bound=new LinkedHashMap<>(entry);bound.put("sourceAssetIds",List.of(sourceIds.get(index++)));entries.add(bound);}
        var book=new LinkedHashMap<>(original);book.put("sheets",entries);sheets.putWorkbook(owner.principal(),id,book,-1,1);
        var settings=new com.axplatform.ai.AiSettings(new org.springframework.mock.env.MockEnvironment().withProperty("ax.integrations.openai.enabled","true").withProperty("ax.integrations.openai.api-key","TEST_ONLY_NOT_A_KEY").withProperty("ax.integrations.openai.model","TEST_ONLY_MODEL").withProperty("ax.integrations.openai.max-output-tokens","2000"));
        var http=new com.axplatform.ai.PhotoBatchHttpFixture();var worker=new AiService(db,access,drafts,sheets,assets,json,clock,settings,http.provider(settings,json),sessions,metering);
        try {
            String key=UUID.randomUUID().toString();var start=new AiService.Start("각 사진을 해당 시트에 옮겨 주세요",sourceIds,key,2,0,null,null,List.of("sheet1","inventory"));
            String run=(String)worker.start(owner.principal(),session,id,start,key).get("id");
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->!Set.of("QUEUED","RUNNING").contains(worker.get(owner.principal(),run).get("status")));
            var ready=worker.get(owner.principal(),run);assertThat(ready.get("status")).as(ready.toString()).isEqualTo("READY");assertThat((List<?>)ready.get("sheetResults")).hasSize(2);
            assertThat(http.generations.get()).isEqualTo(4);assertThat(http.photoCounts).containsOnly(1L);assertThat(http.sheetIds).containsExactly("sheet1","inventory");
            assertThat(db.queryForList("SELECT round_no FROM ap_ai_call WHERE run_id=? ORDER BY round_no",Integer.class,run)).containsExactly(0,1,6,7);
            assertThat(((Number)ready.get("inputTokens")).longValue()).isEqualTo(400);assertThat(((Number)ready.get("outputTokens")).longValue()).isEqualTo(80);
            assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("KEEP-A","r1"));
            String applyKey=UUID.randomUUID().toString();worker.applyBatch(owner.principal(),session,run,new AiService.BatchApply(applyKey,2,0,List.of("sheet1","inventory"),true),applyKey);
            assertThat(WorkbookRules.data(storedBook(owner,id),"sheet1")).isEqualTo(table("PHOTO_1","r1"));assertThat(WorkbookRules.data(storedBook(owner,id),"inventory")).isEqualTo(table("PHOTO_2","r2"));
        } finally {worker.close();}
    }
}
