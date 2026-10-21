package com.axplatform.auth;

import com.axplatform.security.CustomerPrincipal;

import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionLogin {
    private final com.axplatform.operations.ActivityRecorder activity;

    public SessionLogin(com.axplatform.operations.ActivityRecorder activity) {
        this.activity = activity;
    }

    private final HttpSessionSecurityContextRepository contexts =
            new HttpSessionSecurityContextRepository();

    public void login(
            CustomerPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        request.getSession().removeAttribute("SOCIAL_PENDING");
        request.getSession().removeAttribute("SOCIAL_EXISTING");
        request.getSession().removeAttribute("SIGNUP_BINDING");
        request.getSession().removeAttribute("PLAN_POPUP_SEEN");
        activity.login(principal.accountId(), true);
    }
}
