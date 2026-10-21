package com.axplatform.security;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.axplatform.common.Problem;
/** Long responses retain the original session, never just a still-active account. */
public final class SessionLease {
    private SessionLease() {}
    public static String captureId(HttpSession session,SessionSubject expected) {
        require(session,expected);
        try { return session.getId(); } catch(IllegalStateException ended) { throw expired(); }
    }
    /** A principal may compare equal after the same account logs in again; bind the original ID too. */
    public static void require(HttpSession session,SessionSubject expected,String originalId) {
        require(session,expected);
        try { if(originalId==null||!originalId.equals(session.getId()))throw expired(); }
        catch(IllegalStateException ended) { throw expired(); }
    }
    public static void require(HttpSession session,SessionSubject expected) {
        try {
            if(session==null)throw expired();
            Object value=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if(!(value instanceof SecurityContext context)||context.getAuthentication()==null
                    ||!context.getAuthentication().isAuthenticated()
                    ||!expected.equals(context.getAuthentication().getPrincipal()))throw expired();
            int timeout=session.getMaxInactiveInterval();
            if(timeout>0&&System.currentTimeMillis()-session.getLastAccessedTime()>=timeout*1000L)throw expired();
        } catch(IllegalStateException invalidated) { throw expired(); }
    }
    private static Problem expired(){return new Problem(401,"SESSION_EXPIRED","원래 로그인 세션이 종료되었습니다.");}
}
