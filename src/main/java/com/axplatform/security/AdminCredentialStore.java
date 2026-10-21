package com.axplatform.security;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AdminCredentialStore {
    public record Credential(long id, long accountId, String username, String hash,
            long securityVersion, int failures, LocalDateTime lockedUntil,
            String accountType, String role, String status) {
        public boolean eligible() {
            return "SYSTEM_ADMIN".equals(accountType) && "SUPER_ADMIN".equals(role)
                    && "ACTIVE".equals(status);
        }
        @Override public String toString() { return "Credential[id=" + id + "]"; }
    }
    private static final String SELECT = """
            SELECT c.id,c.account_id,c.username,c.password_hash,c.security_version,
                   c.failed_attempts,c.locked_until,a.account_type,a.system_role,a.status
            FROM ap_admin_credential c JOIN ap_account a ON a.id=c.account_id
            """;
    private static final RowMapper<Credential> MAPPER = (rs, n) -> {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new Credential(rs.getLong("id"), rs.getLong("account_id"),
                rs.getString("username"), rs.getString("password_hash"),
                rs.getLong("security_version"), rs.getInt("failed_attempts"),
                locked == null ? null : locked.toLocalDateTime(), rs.getString("account_type"),
                rs.getString("system_role"), rs.getString("status"));
    };
    private final JdbcTemplate jdbc;
    public AdminCredentialStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Credential> lockByUsername(String username) {
        return first(jdbc.query(SELECT + " WHERE c.username=? FOR UPDATE", MAPPER, username));
    }
    public Optional<Credential> findById(long id) {
        return first(jdbc.query(SELECT + " WHERE c.id=?", MAPPER, id));
    }
    public Optional<Credential> lockById(long id) {
        return first(jdbc.query(SELECT + " WHERE c.id=? FOR UPDATE", MAPPER, id));
    }
    private static Optional<Credential> first(List<Credential> rows) { return rows.stream().findFirst(); }
    public void failures(long id, int count, LocalDateTime lockedUntil, LocalDateTime now) {
        jdbc.update("UPDATE ap_admin_credential SET failed_attempts=?,locked_until=?,updated_at=? WHERE id=?",
                count, lockedUntil, now, id);
    }
    public void changePassword(long id, String hash, LocalDateTime now) {
        jdbc.update("""
                UPDATE ap_admin_credential SET password_hash=?,security_version=security_version+1,
                  failed_attempts=0,locked_until=NULL,password_changed_at=?,updated_at=? WHERE id=?
                """, hash, now, now, id);
    }
    public void audit(Long actor, String action, String detail, LocalDateTime now) {
        jdbc.update("INSERT INTO ap_admin_audit(actor_account_id,action,detail,created_at) VALUES (?,?,?,?)",
                actor, action, detail, now);
    }
    public long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("DB가 생성한 ID를 반환하지 않았습니다.");
        return key.longValue();
    }
}
