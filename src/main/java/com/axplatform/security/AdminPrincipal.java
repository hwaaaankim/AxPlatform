package com.axplatform.security;

import java.io.Serializable;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Never holds a password, hash, key, or phone number. */
public record AdminPrincipal(long accountId, long credentialId, long securityVersion,
                             String username) implements SessionSubject, Serializable {
    @Override public boolean administrator() { return true; }
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }
    @Override public String toString() { return "AdminPrincipal[accountId=" + accountId + "]"; }
}
