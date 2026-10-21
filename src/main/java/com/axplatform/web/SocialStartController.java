package com.axplatform.web;

import java.util.*;
import com.axplatform.auth.AuthSettings;
import com.axplatform.common.Problem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller public class SocialStartController {
    private final AuthSettings cfg;
    public SocialStartController(AuthSettings cfg) {
        this.cfg=cfg;
    }
    @GetMapping("/oauth2/start/{provider}") public String start(@PathVariable String provider,@RequestParam(defaultValue="LOGIN") String intent,HttpServletRequest r) {
        if(!Set.of("google","kakao","naver").contains(provider)||!cfg.enabled("ax.integrations."+provider+"-login.enabled"))throw new Problem(503,"OAUTH_NOT_CONNECTED","SNS 로그인을 연결하지 않았습니다.");
        if(!Set.of("LOGIN","SIGNUP").contains(intent))throw Problem.invalid("가입·로그인 목적을 확인해 주세요.");
        r.getSession().removeAttribute("SOCIAL_PENDING");
        r.getSession().removeAttribute("SOCIAL_EXISTING");
        r.getSession().setAttribute("SOCIAL_INTENT",intent);
        return "redirect:/oauth2/authorization/"+provider;
    }
}
