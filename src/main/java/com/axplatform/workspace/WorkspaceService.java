package com.axplatform.workspace;

import java.util.List;
import com.axplatform.security.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
    public record Scope(long workspaceId, String workspaceName, long businessId, String businessName) { }
    private final JdbcTemplate jdbc;
    public WorkspaceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Scope> ownTestScopes(AdminPrincipal principal) {
        return jdbc.query("""
                SELECT w.id,w.name,b.id AS business_id,b.name AS business_name
                FROM ap_workspace w JOIN ap_workspace_member m ON m.workspace_id=w.id
                JOIN ap_business_scope b ON b.workspace_id=w.id
                WHERE w.owner_account_id=? AND m.account_id=? AND w.kind='ADMIN_TEST'
                ORDER BY w.id,b.id
                """, (rs, n) -> new Scope(rs.getLong("id"), rs.getString("name"),
                rs.getLong("business_id"), rs.getString("business_name")), principal.accountId(), principal.accountId());
    }
}
