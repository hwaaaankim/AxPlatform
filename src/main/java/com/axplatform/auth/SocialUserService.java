package com.axplatform.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import com.axplatform.common.JsonCodec;
import org.springframework.stereotype.Component;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
@Component public class SocialUserService implements OAuth2UserService<OAuth2UserRequest,OAuth2User> {
    private final JsonCodec json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public SocialUserService(JsonCodec json) {
        this.json=json;
    }
    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        String provider=request.getClientRegistration().getRegistrationId();
        if(!Set.of("kakao","naver").contains(provider))throw failed();
        String endpoint=provider.equals("kakao")?"https://kapi.kakao.com/v2/user/me":"https://openapi.naver.com/v1/nid/me";
        try {
            var response=client.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+request.getAccessToken().getTokenValue()).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200||response.body().length()>200000)throw failed();
            Map<String,Object> raw=json.object(response.body());
            Map<String,Object> account;
            if(provider.equals("kakao"))account=map(raw.get("kakao_account"));
            else {
                if(!"00".equals(raw.get("resultcode")))throw failed();
                account=map(raw.get("response"));
            }
            Object id=provider.equals("kakao")?raw.get("id"):account.get("id");
            if(id==null||id.toString().isBlank())throw failed();
            var attributes=new LinkedHashMap<String,Object>();
            attributes.put("subject",id.toString());
            attributes.put("email",string(account.get("email")));
            attributes.put("emailVerified",provider.equals("kakao")&&Boolean.TRUE.equals(account.get("is_email_valid"))&&Boolean.TRUE.equals(account.get("is_email_verified")));
            attributes.put("name",provider.equals("kakao")?string(map(account.get("profile")).get("nickname")):string(account.get("name")));
            attributes.put("phone",string(account.get(provider.equals("kakao")?"phone_number":"mobile")));
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_OAUTH_PENDING")),attributes,"subject");
        }
        catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            throw failed();
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) {
        return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
    }
    private String string(Object value) {
        return value==null?"":value.toString();
    }
    private OAuth2AuthenticationException failed() {
        return new OAuth2AuthenticationException(new OAuth2Error("profile_unavailable"),"SNS 프로필을 확인하지 못했습니다.");
    }
}
