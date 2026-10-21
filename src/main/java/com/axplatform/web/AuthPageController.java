package com.axplatform.web;

import java.util.*;
import com.axplatform.auth.*;
import com.axplatform.common.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller public class AuthPageController {
    private final CustomerAccountService accounts;
    private final AuthRateService rate;
    private final SessionLogin login;
    private final AuthSettings cfg;
    public AuthPageController(CustomerAccountService accounts,AuthRateService rate,SessionLogin login,AuthSettings cfg) {
        this.accounts=accounts;
        this.rate=rate;
        this.login=login;
        this.cfg=cfg;
    }
    @PostMapping("/login") public String login(@RequestParam String username,@RequestParam String password,HttpServletRequest request,HttpServletResponse response) {
        try {
            rate.limit("CUSTOMER_LOGIN_IP",request.getRemoteAddr(),30,1);
            var found=accounts.authenticate(username,password);
            if(found.isEmpty())return "redirect:/login?error";
            login.login(found.get(),request,response);
            return "redirect:/dashboard";
        }
        catch(Problem p) {
            return "redirect:/login?error";
        }
    }
    @GetMapping("/signup") public String signup(HttpServletRequest r,Model m) {
        var s=r.getSession().getAttribute("SOCIAL_PENDING");
        m.addAttribute("social",s instanceof CustomerAccountService.SocialPending p?p:null);
        return "auth/signup";
    }
    @GetMapping("/recovery") public String recovery() {
        return "auth/recovery";
    }
    @GetMapping("/password/reset") public String reset(@RequestParam(defaultValue="") String token,Model m) {
        m.addAttribute("resetToken",token.matches("[A-Za-z0-9_-]{43}")?token:"");
        return "auth/reset";
    }
    @GetMapping("/terms") public String terms(Model m) {
        m.addAttribute("kind","terms");
        m.addAttribute("version",cfg.value("ax.auth.terms-version","2026-10-09-DRAFT"));
        return "auth/terms";
    }
    @GetMapping("/privacy") public String privacy(Model m) {
        m.addAttribute("kind","privacy");
        m.addAttribute("version",cfg.value("ax.auth.privacy-version","2026-10-09-DRAFT"));
        return "auth/terms";
    }
    @GetMapping("/signup/social-existing") public String existing(HttpServletRequest r) {
        if(r.getSession().getAttribute("SOCIAL_EXISTING")==null)return "redirect:/login";
        return "auth/social-existing";
    }
    @PostMapping("/signup/social-existing") public String continueLogin(HttpServletRequest r,HttpServletResponse response) {
        Object v=r.getSession().getAttribute("SOCIAL_EXISTING");
        if(!(v instanceof CustomerAccountService.SocialPending p)||!p.expires().isAfter(java.time.Instant.now()))return "redirect:/login?socialError";
        var found=accounts.social(p.provider(),p.subject());
        if(found.isEmpty())return "redirect:/login?socialError";
        login.login(found.get(),r,response);
        return "redirect:/dashboard";
    }
}
