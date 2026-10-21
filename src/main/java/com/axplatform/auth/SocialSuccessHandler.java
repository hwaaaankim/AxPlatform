package com.axplatform.auth;

import java.io.IOException;
import java.time.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
@Component public class SocialSuccessHandler implements AuthenticationSuccessHandler {
    private final CustomerAccountService accounts;
    private final SessionLogin login;
    private final ObjectProvider<OAuth2AuthorizedClientService> tokens;
    private final Clock clock;
    public SocialSuccessHandler(CustomerAccountService accounts,SessionLogin login,ObjectProvider<OAuth2AuthorizedClientService> tokens,Clock clock) {
        this.accounts=accounts;
        this.login=login;
        this.tokens=tokens;
        this.clock=clock;
    }
    @Override public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws IOException {
        if(!(authentication instanceof OAuth2AuthenticationToken auth)) {
            response.sendRedirect("/login?socialError");
            return;
        }
        String provider=auth.getAuthorizedClientRegistrationId().toUpperCase(java.util.Locale.ROOT);
        var user=auth.getPrincipal();
        String subject,email,name,phone;
        boolean verified;
        if(user instanceof OidcUser oidc) {
            subject=oidc.getSubject();
            email=oidc.getEmail()==null?"":oidc.getEmail();
            verified=Boolean.TRUE.equals(oidc.getEmailVerified());
            name=oidc.getFullName()==null?"":oidc.getFullName();
            phone="";
        }
        else {
            subject=(String)user.getAttributes().get("subject");
            email=(String)user.getAttributes().getOrDefault("email","");
            name=(String)user.getAttributes().getOrDefault("name","");
            phone=(String)user.getAttributes().getOrDefault("phone","");
            verified=Boolean.TRUE.equals(user.getAttributes().get("emailVerified"));
        }
        tokens.ifAvailable(service->service.removeAuthorizedClient(auth.getAuthorizedClientRegistrationId(),auth.getName()));
        SecurityContextHolder.clearContext();
        new HttpSessionSecurityContextRepository().saveContext(SecurityContextHolder.createEmptyContext(),request,response);
        var session=request.getSession();
        request.changeSessionId();
        String intent=(String)session.getAttribute("SOCIAL_INTENT");
        session.removeAttribute("SOCIAL_INTENT");
        if(subject==null||subject.isBlank()) {
            response.sendRedirect("/login?socialError");
            return;
        }
        try {
            email=com.axplatform.common.Inputs.email(email);
        }
        catch(com.axplatform.common.Problem invalid) {
            email="";
            verified=false;
        }
        var pending=new CustomerAccountService.SocialPending(provider,subject,email,verified,name,phone,Instant.now(clock).plusSeconds(1800));
        if(accounts.socialExists(provider,subject)) {
            var existing=accounts.social(provider,subject);
            if(existing.isEmpty()) {
                response.sendRedirect("/login?socialError");
                return;
            }
            if("SIGNUP".equals(intent)) {
                session.setAttribute("SOCIAL_EXISTING",pending);
                response.sendRedirect("/signup/social-existing");
            }
            else {
                login.login(existing.get(),request,response);
                response.sendRedirect("/dashboard");
            }
        }
        else {
            session.setAttribute("SOCIAL_PENDING",pending);
            response.sendRedirect("/signup");
        }
    }
}
