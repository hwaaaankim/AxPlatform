package com.axplatform.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
/** PKCE is enabled for Google; no assumption that unverified custom providers support it. */
public class ProviderAuthorizationResolver implements OAuth2AuthorizationRequestResolver {
    private final DefaultOAuth2AuthorizationRequestResolver standard,pkce;
    public ProviderAuthorizationResolver(ClientRegistrationRepository clients) {
        standard=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        pkce=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        pkce.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest r) {
        return (r.getRequestURI().endsWith("/google")?pkce:standard).resolve(r);
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest r,String id) {
        return ("google".equals(id)?pkce:standard).resolve(r,id);
    }
}
