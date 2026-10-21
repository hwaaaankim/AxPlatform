package com.axplatform.auth;

import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import com.axplatform.common.Problem;
@Component public class AuthSettings {
    private final Environment env;
    public AuthSettings(Environment env) {
        this.env=env;
    }
    public String value(String key,String fallback) {
        return env.getProperty(key,fallback);
    }
    public boolean enabled(String key) {
        return env.getProperty(key,Boolean.class,false);
    }
    public int integer(String key,int fallback) {
        return env.getProperty(key,Integer.class,fallback);
    }
    public boolean supplied(String key) {
        String v=value(key,"");
        return !v.isBlank()&&!v.startsWith("CHANGE_ME");
    }
    public String secret() {
        String s=value("ax.auth.otp-secret","");
        if(s.length()<32||s.startsWith("CHANGE_ME"))throw new Problem(503,"AUTH_CONFIGURATION","개인 설정파일의 ax.auth.otp-secret에 32자 이상의 무작위 비밀값을 입력해 주세요.");
        return s;
    }
    public String base() {
        String s=value("ax.public-base-url","http://localhost:8080").replaceAll("/+$","");
        URI u;
        try {
            u=URI.create(s);
        }
        catch(Exception e) {
            throw Problem.invalid("공개 base URL 설정을 확인해 주세요.");
        }
        boolean local=SetHelper.local(u.getHost());
        if(u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getHost()==null||(!"https".equals(u.getScheme())&&!("http".equals(u.getScheme())&&local))||(u.getPath()!=null&&!u.getPath().isEmpty()))throw new Problem(503,"BASE_URL_CONFIGURATION","base URL은 HTTPS 주소 또는 로컬 localhost 주소로 설정해 주세요.");
        return s;
    }
    public boolean registration() {
        return enabled("ax.auth.registration-enabled");
    }
    private static class SetHelper {
        static boolean local(String host) {
            return "localhost".equals(host)||"127.0.0.1".equals(host)||"::1".equals(host);
        }
    }
}
