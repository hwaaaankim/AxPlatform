package com.axplatform.web;

import java.util.List;
import com.axplatform.identity.MemberQueryService;
import com.axplatform.security.AdminAuthenticationService;
import com.axplatform.security.AdminPrincipal;
import com.axplatform.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller public class PageController {
    private final MemberQueryService members;
    private final WorkspaceService workspaces;
    private final AdminAuthenticationService authentication;
    private final JdbcTemplate jdbc;
    private final String displayName;
    public PageController(MemberQueryService members, WorkspaceService workspaces,             AdminAuthenticationService authentication, JdbcTemplate jdbc,             @Value("${ax.display-name:AxPlatform}") String displayName) {
        this.members = members;
        this.workspaces = workspaces;
        this.authentication = authentication;
        this.jdbc = jdbc;
        this.displayName = displayName;
    }
    @ModelAttribute("appName") public String appName() {
        return displayName;
    }
    @GetMapping("/") public String root() {
        return "redirect:/login";
    }
    @GetMapping("/login") public String login() {
        return "auth/login";
    }
    @GetMapping("/admin/login") public String adminLogin() {
        return "auth/admin-login";
    }
    @GetMapping("/admin") public String admin(@AuthenticationPrincipal AdminPrincipal principal, Model model) {
        model.addAttribute("memberCount", members.search("", 1).total());
        model.addAttribute("scopes", workspaces.ownTestScopes(principal));
        return "admin/index";
    }
    @GetMapping("/admin/members") public String members(@RequestParam(defaultValue="") String query,             @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="ALL") String method, @RequestParam(defaultValue="CUSTOMER") String type, Model model) {
        model.addAttribute("result", members.search(query, page,status,method,type));
        return "admin/members";
    }
    @GetMapping("/admin/members/{id}") public String memberDetail(@PathVariable long id, Model model) {
        model.addAttribute("memberId", id);
        return "admin/member-detail";
    }
    @GetMapping("/fragments/admin/members") public String memberFragment(@RequestParam(defaultValue="") String query,             @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="ALL") String method, @RequestParam(defaultValue="CUSTOMER") String type, Model model) {
        model.addAttribute("result", members.search(query, page,status,method,type));
        return "admin/members :: memberTable";
    }
    @GetMapping("/admin/permissions") public String permissions(Model model) {
        model.addAttribute("tiers", jdbc.queryForList("SELECT code,name,plan_code FROM ap_access_tier ORDER BY sort_order"));
        model.addAttribute("groups", jdbc.queryForList("SELECT code,name,status FROM ap_feature_group ORDER BY sort_order"));
        return "admin/permissions";
    }
    @GetMapping("/admin/system") public String system(Model model) {
        model.addAttribute("migration", jdbc.queryForList("SELECT version,description,applied_at FROM ap_schema_migration ORDER BY version"));
        model.addAttribute("initialized", jdbc.queryForObject("SELECT COUNT(*) FROM ap_bootstrap WHERE name='INITIAL_ADMIN' AND completed_at IS NOT NULL", Integer.class) == 1);
        return "admin/system";
    }
    @GetMapping("/admin/password") public String password(@AuthenticationPrincipal AdminPrincipal principal, Model model) {
        model.addAttribute("securityVersion", principal.securityVersion());
        return "admin/password";
    }
    @PostMapping("/admin/password") public String changePassword(@AuthenticationPrincipal AdminPrincipal principal,             @RequestParam String currentPassword, @RequestParam String newPassword,             @RequestParam String confirmation, @RequestParam long securityVersion,             HttpServletRequest request, HttpServletResponse response, Model model) {
        var failure = authentication.changePassword(principal, securityVersion, currentPassword, newPassword, confirmation);
        if (failure != null && failure.isPresent()) {
            response.setStatus(400);
            model.addAttribute("errorMessage", failure.get());
            model.addAttribute("securityVersion", principal.securityVersion());
            return "admin/password";
        }
        new SecurityContextLogoutHandler().logout(request, response,                 org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/admin/login?passwordChanged";
    }
    @GetMapping("/workspace") public String workspace() {
        return "redirect:/dashboard";
    }
    public record Feature(String name, String description, String stage) {
    }
}
