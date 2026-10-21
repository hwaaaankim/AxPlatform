package com.axplatform;

import com.axplatform.security.*;
import com.axplatform.workspace.DraftService;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
 "spring.config.location=optional:classpath:/test-empty.yml",
 "spring.datasource.url=jdbc:h2:mem:axplatform_ui_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/test-schema.sql",
 "spring.jpa.hibernate.ddl-auto=validate","ax.integrations.openai.enabled=false","ax.storage-root=./target/ui-test-assets"
}) @AutoConfigureMockMvc
class V004UiContractTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired DraftService drafts;
 @Test void allExistingPageRoutesRenderAndExportSanitizedFixturesForDomTests()throws Exception {
  Path output=Path.of("target/ui-rendered");Files.createDirectories(output);
  for(String route:List.of("/login","/admin/login","/signup","/recovery","/password/reset","/terms","/privacy"))capture(route,null,output);
  MockHttpSession session=(MockHttpSession)mvc.perform(post("/admin/login").with(csrf()).param("username","admin").param("password","12345")).andExpect(redirectedUrl("/admin")).andReturn().getRequest().getSession(false);
  for(String route:List.of("/admin","/admin/members","/admin/permissions","/admin/password","/admin/system","/dashboard","/my/files","/admin/files","/admin/operations"))capture(route,session,output);
  mvc.perform(post("/api/admin/ai/diagnostics").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/ai/diagnostics").session(session).with(csrf())).andExpect(status().isServiceUnavailable());
  long admin=db.queryForObject("SELECT id FROM ap_account WHERE account_type='SYSTEM_ADMIN'",Long.class);
  capture("/admin/members/"+admin,session,output);
  long business=db.queryForObject("SELECT MIN(id) FROM ap_business_scope",Long.class);
  for(String workflow:List.of("EXCEL","WORD","HWP","PPT","CRM","CALENDAR","APP"))capture("/work/new?businessId="+business+"&workflow="+workflow,session,output);
  for(String route:List.of("/api/me","/api/business-scopes?workspaceId=1","/api/workspaces/1/access","/api/workspaces/1/dashboard","/api/assets?businessId="+business,"/api/drafts?businessId="+business,"/api/ai/settings")){
    var result=mvc.perform(get(route).session(session)).andExpect(status().isOk()).andReturn();Files.writeString(output.resolve(route.replaceAll("[^a-zA-Z0-9_-]","_")+".json"),result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
 }
 private void capture(String route,MockHttpSession session,Path dir)throws Exception {
  var req=get(route);if(session!=null)req.session(session);var r=mvc.perform(req).andExpect(status().isOk()).andReturn();String html=r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  assertThat(html).contains("/css/layout.css?v=0.6.0","/css/workspace.css?v=0.6.0","/css/form-controls.css?v=0.6.0","/js/app-shell.js?v=0.6.0").doesNotContain("th:replace","<script>"," style=");
  assertThat(html.split("class=\"topbar\"",-1).length-1).as("exactly one topbar on %s",route).isEqualTo(1);
  assertThat(html.split("class=\"brand\"",-1).length-1).as("exactly one brand on %s",route).isEqualTo(1);
  assertThat(r.getResponse().getHeader("Content-Security-Policy")).contains("script-src 'self'");
  if(route.startsWith("/work/new"))assertThat(html).contains("data-stage-panel=\"start\"","data-stage-panel=\"edit\"","analysis-panel");
  if(route.equals("/admin/system"))assertThat(html).contains("id=\"ai-connection-check\"","/js/admin-system.js?v=0.6.0");
  Files.writeString(dir.resolve(route.replaceAll("[^a-zA-Z0-9_-]","_")+".html"),html);
 }
}
