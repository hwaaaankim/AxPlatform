package com.axplatform.config;

import com.axplatform.auth.*;
import com.axplatform.security.*;
import com.axplatform.web.ApiErrorWriter;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminAuthenticationProvider provider,
            AccountSessionService sessions,
            ObjectProvider<ClientRegistrationRepository> clients,
            SocialUserService users,
            SocialSuccessHandler success)
            throws Exception {
        http.authenticationProvider(provider)
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(
                                                DispatcherType.ERROR, DispatcherType.ASYNC)
                                        .permitAll()
                                        .requestMatchers(
                                                "/",
                                                "/login",
                                                "/admin/login",
                                                "/signup",
                                                "/signup/**",
                                                "/recovery",
                                                "/password/reset",
                                                "/terms",
                                                "/privacy",
                                                "/api/auth/**",
                                                "/oauth2/**",
                                                "/login/oauth2/code/**",
                                                "/css/**",
                                                "/js/**",
                                                "/error",
                                                "/api/session/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                "/admin/**", "/api/admin/**", "/fragments/admin/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(
                                                "/dashboard",
                                                "/my/**",
                                                "/api/my/**",
                                                "/api/sheets/**",
                                                "/workspace",
                                                "/work/**",
                                                "/api/me",
                                                "/api/workspaces/**",
                                                "/api/business-scopes/**",
                                                "/api/drafts/**",
                                                "/api/assets/**",
                                                "/api/preferences/**",
                                                "/api/events",
                                                "/api/ai/settings",
                                                "/api/ai-runs/**")
                                        .hasAnyRole("SUPER_ADMIN", "CUSTOMER")
                                        .anyRequest()
                                        .denyAll())
                .formLogin(
                        form ->
                                form.loginPage("/admin/login")
                                        .loginProcessingUrl("/admin/login")
                                        .defaultSuccessUrl("/admin", true)
                                        .failureUrl("/admin/login?error")
                                        .permitAll())
                .logout(
                        logout ->
                                logout.logoutUrl("/logout")
                                        .logoutSuccessUrl("/login?logout")
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID"))
                .requestCache(cache -> cache.disable())
                .sessionManagement(
                        session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, error) -> {
                                                    if (ApiErrorWriter.isAsync(request))
                                                        ApiErrorWriter.write(
                                                                response,
                                                                401,
                                                                "AUTH_REQUIRED",
                                                                "로그인이 필요합니다.");
                                                    else
                                                        response.sendRedirect(
                                                                request.getRequestURI()
                                                                                .startsWith(
                                                                                        "/admin")
                                                                        ? "/admin/login"
                                                                        : "/login");
                                                })
                                        .accessDeniedHandler(
                                                (request, response, error) -> {
                                                    if (ApiErrorWriter.isAsync(request))
                                                        ApiErrorWriter.write(
                                                                response,
                                                                403,
                                                                "ACCESS_DENIED",
                                                                "권한 또는 요청 인증값을 확인해 주세요.");
                                                    else response.sendError(403);
                                                }))
                .headers(
                        headers ->
                                headers.referrerPolicy(
                                                ref ->
                                                        ref.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER))
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src"
                                                                    + " 'self'; style-src 'self';"
                                                                    + " img-src 'self' data:;"
                                                                    + " object-src 'none'; base-uri"
                                                                    + " 'self'; frame-ancestors"
                                                                    + " 'none'; form-action"
                                                                    + " 'self'")))
                .addFilterBefore(new AdminSessionGuard(sessions), AuthorizationFilter.class);
        ClientRegistrationRepository registrations = clients.getIfAvailable();
        if (registrations != null)
            http.oauth2Login(
                    oauth ->
                            oauth.loginPage("/login")
                                    .authorizationEndpoint(
                                            endpoint ->
                                                    endpoint.authorizationRequestResolver(
                                                            new ProviderAuthorizationResolver(
                                                                    registrations)))
                                    .userInfoEndpoint(info -> info.userService(users))
                                    .successHandler(success)
                                    .failureHandler(
                                            (request, response, error) ->
                                                    response.sendRedirect("/login?socialError")));
        return http.build();
    }
}
