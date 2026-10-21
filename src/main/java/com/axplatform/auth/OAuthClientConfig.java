package com.axplatform.auth;

import java.util.*;
import com.axplatform.common.Problem;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
@Configuration @ConditionalOnExpression("${ax.integrations.google-login.enabled:false} or ${ax.integrations.kakao-login.enabled:false} or ${ax.integrations.naver-login.enabled:false}") public class OAuthClientConfig {
    @Bean public ClientRegistrationRepository clients(AuthSettings cfg) {
        List<ClientRegistration> registrations=new ArrayList<>();
        String base=cfg.base();
        for(String provider:List.of("google","kakao","naver")) {
            String prefix="ax.integrations."+provider+"-login.";
            if(!cfg.enabled(prefix+"enabled"))continue;
            if(!cfg.supplied(prefix+"client-id")||(!cfg.supplied(prefix+"client-secret")))throw new Problem(503,"OAUTH_CONFIGURATION",provider+" client-id/client-secret을 개인 파일에 입력해 주세요.");
            String id=cfg.value(prefix+"client-id",""),secret=cfg.value(prefix+"client-secret","");
            ClientRegistration.Builder b;
            if(provider.equals("google"))b=CommonOAuth2Provider.GOOGLE.getBuilder("google").issuerUri("https://accounts.google.com");
            else {
                b=ClientRegistration.withRegistrationId(provider).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).clientName(provider).scope(provider.equals("kakao")?List.of("profile_nickname","account_email"):List.of());
                if(provider.equals("kakao"))b.authorizationUri("https://kauth.kakao.com/oauth/authorize").tokenUri("https://kauth.kakao.com/oauth/token").userInfoUri("https://kapi.kakao.com/v2/user/me").userNameAttributeName("id");
                else b.authorizationUri("https://nid.naver.com/oauth2.0/authorize").tokenUri("https://nid.naver.com/oauth2.0/token").userInfoUri("https://openapi.naver.com/v1/nid/me").userNameAttributeName("id");
            }
            b.clientId(id).clientSecret(secret).redirectUri(base+"/login/oauth2/code/"+provider).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            registrations.add(b.build());
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }
    @Bean public OAuth2AuthorizedClientService authorizedClients(ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }
}
