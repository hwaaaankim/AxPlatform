package com.axplatform.security;

import java.time.Clock;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {
    private final AdminAuthenticationService service;
    private final LoginAttemptLimiter limiter;
    public AdminAuthenticationProvider(AdminAuthenticationService service, Clock clock) {
        this.service = service; this.limiter = new LoginAttemptLimiter(clock);
    }
    @Override public Authentication authenticate(Authentication authentication) {
        String address = authentication.getDetails() instanceof WebAuthenticationDetails d
                ? d.getRemoteAddress() : "unknown";
        if (!limiter.allow(address)) throw failed();
        String password = authentication.getCredentials() instanceof String s ? s : null;
        AdminPrincipal principal = service.authenticate(authentication.getName(), password).orElseThrow(this::failed);
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        authenticated.setDetails(authentication.getDetails());
        return authenticated;
    }
    private BadCredentialsException failed() { return new BadCredentialsException("관리자 인증 정보를 확인해 주세요."); }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}
