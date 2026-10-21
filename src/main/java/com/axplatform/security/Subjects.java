package com.axplatform.security;

import com.axplatform.common.Problem;
import org.springframework.security.core.context.SecurityContextHolder;
public final class Subjects {
    private Subjects() {
    }
    public static SessionSubject current() {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!a.isAuthenticated()||!(a.getPrincipal() instanceof SessionSubject s))throw new Problem(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        return s;
    }
}
