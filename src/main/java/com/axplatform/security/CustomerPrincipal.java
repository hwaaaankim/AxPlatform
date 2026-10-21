package com.axplatform.security;
import java.io.Serializable;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
public record CustomerPrincipal(long accountId,long accountVersion,long credentialVersion,String loginMethod,String displayName)
 implements SessionSubject,Serializable {
 @Override public boolean administrator(){return false;}
 public List<GrantedAuthority> authorities(){return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));}
}
