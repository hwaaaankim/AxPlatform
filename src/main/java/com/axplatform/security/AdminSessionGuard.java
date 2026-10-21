package com.axplatform.security;

import java.io.IOException;
import com.axplatform.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Added to the Spring Security chain only; not registered a second time as a servlet filter. */
public class AdminSessionGuard extends OncePerRequestFilter {
    private final AccountSessionService service;
    public AdminSessionGuard(AccountSessionService service) { this.service = service; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            boolean current;
            try {
                current = authentication.getPrincipal() instanceof SessionSubject p && service.current(p);
            } catch (DataAccessException unavailable) {
                if (ApiErrorWriter.isAsync(request)) ApiErrorWriter.write(response, 503, "AUTH_UNAVAILABLE", "인증 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
                else response.sendError(503);
                return;
            }
            if (!current) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                if (ApiErrorWriter.isAsync(request)) ApiErrorWriter.write(response, 401, "SESSION_EXPIRED", "인증 정보가 변경되었습니다. 다시 로그인해 주세요.");
                else response.sendRedirect(authentication.getPrincipal() instanceof AdminPrincipal ? "/admin/login?expired" : "/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
