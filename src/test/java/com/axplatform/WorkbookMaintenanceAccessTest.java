package com.axplatform;

import com.axplatform.common.JsonCodec;
import com.axplatform.security.CustomerPrincipal;
import com.axplatform.sheet.*;
import com.axplatform.workspace.DraftService;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.config.location=optional:classpath:/test-empty.yml",
 "spring.datasource.url=jdbc:h2:mem:axplatform_maintenance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/test-schema.sql",
 "spring.jpa.hibernate.ddl-auto=validate","ax.integrations.openai.enabled=false","ax.storage-root=./target/maintenance-test-assets"
})
@AutoConfigureMockMvc
class WorkbookMaintenanceAccessTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired JsonCodec json;
 @Autowired SheetService sheets; @Autowired DraftService drafts;
 record Owner(CustomerPrincipal principal,long business){}
 private Owner owner(){
  String name=UUID.randomUUID().toString(),email=name+"@example.invalid";var now=LocalDateTime.now();
  db.update("INSERT INTO ap_account(account_type,system_role,status,display_name,contact_email,phone_e164,created_at,updated_at) VALUES('CUSTOMER','CUSTOMER','ACTIVE','Maintenance test',?,'+821012345678',?,?)",email,now,now);
  long id=db.queryForObject("SELECT id FROM ap_account WHERE contact_email=?",Long.class,email);
  db.update("INSERT INTO ap_email_credential(account_id,login_email,password_hash,email_verified_at) VALUES(?,?,?,?)",id,email,"TEST_ONLY",now);
  db.update("INSERT INTO ap_workspace(owner_account_id,name,kind,plan_code,created_at,updated_at) VALUES(?,?,'CUSTOMER','FREE',?,?)",id,name,now,now);
  long workspace=db.queryForObject("SELECT id FROM ap_workspace WHERE owner_account_id=?",Long.class,id);
  db.update("INSERT INTO ap_workspace_member(workspace_id,account_id,member_role,created_at) VALUES(?,?,'OWNER',?)",workspace,id,now);
  db.update("INSERT INTO ap_business_scope(workspace_id,name,is_default,created_at,updated_at) VALUES(?,'Maintenance test',TRUE,?,?)",workspace,now,now);
  return new Owner(new CustomerPrincipal(id,0,0,"EMAIL","Maintenance test"),db.queryForObject("SELECT id FROM ap_business_scope WHERE workspace_id=?",Long.class,workspace));
 }
 private void grant(String... actions){
  long policy=db.queryForObject("SELECT active_policy_id FROM ap_policy_state WHERE id=1",Long.class);
  for(String action:actions)db.update("INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT ?,'GUEST',?,999 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=? AND tier_code='GUEST' AND action_code=?)",policy,action,policy,action);
 }
 private MockHttpSession session(CustomerPrincipal principal){var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.authorities()));var s=new MockHttpSession();s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);return s;}
 @Test void previewEnforcesCsrfTenantAndRowPermissionsAndNeverWritesTheProject() throws Exception {
  try(var connection=Objects.requireNonNull(db.getDataSource()).getConnection()){assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:axplatform_maintenance");}
  db.update("DELETE FROM ap_policy_grant WHERE action_code LIKE 'workspace.sheet.%'");
  grant("workspace.sheet.read","workspace.sheet.update","workspace.sheet.schema.update","workspace.sheet.rows.create","workspace.sheet.cells.update");
  var owner=owner();String id=(String)drafts.create(owner.principal(),new DraftService.Create(owner.business(),"EXCEL","기록")).get("id");
  var table=Map.<String,Object>of("columns",List.of(Map.of("id","name","label","업체명","type","TEXT","unit","","width",160)),"rows",List.of(List.of("기존")),"rowIds",List.of("r1"));
  var saved=sheets.put(owner.principal(),id,table,-1,0);var book=WorkbookRules.normalize(table);
  var payload=Map.of("workbook",book,"sheetId","sheet1","rows",List.of(List.of("추가")),"mapping",List.of("name"),"mode","append","formulaPolicy","keep","extendFormulas",true);
  String url="/api/sheets/workbook-rows?businessId="+owner.business(),body=json.write(payload);var session=session(owner.principal());
  mvc.perform(post(url).session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(url).session(session(owner().principal())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
  db.update("DELETE FROM ap_policy_grant WHERE action_code='workspace.sheet.rows.create'");
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
  grant("workspace.sheet.rows.create");
  var invalid=new LinkedHashMap<String,Object>(payload);invalid.remove("mode");
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(json.write(invalid))).andExpect(status().isBadRequest());
  invalid=new LinkedHashMap<>(payload);invalid.remove("formulaPolicy");
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(json.write(invalid))).andExpect(status().isBadRequest());
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.sheets[0].data.rows.length()").value(2)).andExpect(jsonPath("$.sheets[0].data.rows[1][0]").value("추가"));
  assertThat(sheets.get(owner.principal(),id).get("sheet")).isEqualTo(table);
  assertThat(db.queryForObject("SELECT version FROM ap_sheet WHERE draft_id=?",Long.class,id)).isEqualTo(0);
  assertThat(db.queryForObject("SELECT version FROM ap_work_draft WHERE id=?",Long.class,id)).isEqualTo(1);
  var replacement=new LinkedHashMap<String,Object>(payload);replacement.put("mode","replace");replacement.put("formulaPolicy","discard");
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(json.write(replacement))).andExpect(status().isForbidden());
  grant("workspace.sheet.rows.delete");
  mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(json.write(replacement))).andExpect(status().isOk()).andExpect(jsonPath("$.sheets[0].data.rows.length()").value(1));
  assertThat(sheets.get(owner.principal(),id).get("sheet")).isEqualTo(table);
  SecurityContextHolder.clearContext();
 }
}
