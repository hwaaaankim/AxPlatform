package com.axplatform.identity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "ap_account")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_type", nullable = false, length = 32)
    private String accountType;
    @Column(name = "system_role", nullable = false, length = 32)
    private String systemRole;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private long version;

    protected Account() { }
    public Long getId() { return id; }
    public String getAccountType() { return accountType; }
    public String getSystemRole() { return systemRole; }
    public String getStatus() { return status; }
    public String getDisplayName() { return displayName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
