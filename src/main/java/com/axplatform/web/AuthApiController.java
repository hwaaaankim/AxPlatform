package com.axplatform.web;

import java.util.*;
import com.axplatform.auth.*;
import com.axplatform.common.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthApiController {
    public record Issue(String purpose,String target) {
    }
    public record Confirm(String code) {
    }
    public record Recover(String purpose,String email,String phone) {
    }
    public record Reset(String token,String password,String confirmation) {
    }
    private final VerificationService codes;
    private final CustomerAccountService accounts;
    private final RecoveryService recovery;
    private final SessionLogin login;
    private final AuthRateService rate;
    public AuthApiController(VerificationService codes,CustomerAccountService accounts,RecoveryService recovery,SessionLogin login,AuthRateService rate) {
        this.codes=codes;
        this.accounts=accounts;
        this.recovery=recovery;
        this.login=login;
        this.rate=rate;
    }
    @PostMapping("/verification") public Object issue(@RequestBody Issue i,HttpServletRequest request) {
        return codes.issue(i.purpose(),i.target(),request.getSession(),request.getRemoteAddr());
    }
    @PostMapping("/verification/{id}/confirm") public Map<String,Object> confirm(@PathVariable String id,@RequestBody Confirm i,HttpServletRequest r) {
        rate.limit("OTP_VERIFY_IP",r.getRemoteAddr(),60,10);
        if(!codes.verify(id,i.code(),r.getSession()))throw new Problem(422,"OTP_INVALID","인증번호가 다르거나 만료되었습니다. 다시 확인해 주세요.");
        return Map.of("verified",true,"proof",id);
    }
    @PostMapping("/signup") public Map<String,String> signup(@RequestBody CustomerAccountService.Signup i,HttpServletRequest request,HttpServletResponse response) {
        rate.limit("SIGNUP_IP",request.getRemoteAddr(),10,60);
        var p=accounts.signup(i,request.getSession());
        login.login(p,request,response);
        return Map.of("next","/dashboard");
    }
    @PostMapping("/recovery") public Map<String,String> recover(@RequestBody Recover i,HttpServletRequest r) {
        if("ID".equals(i.purpose()))recovery.findId(i.phone(),r.getRemoteAddr());
        else if("PASSWORD".equals(i.purpose()))recovery.requestReset(i.email(),i.phone(),r.getRemoteAddr());
        else throw Problem.invalid("복구 목적을 선택해 주세요.");
        return Map.of("message","일치하는 이메일 계정이 있으면 등록된 연락처로 안내를 요청했습니다. 도착하지 않으면 연결 설정과 입력값을 확인해 주세요.");
    }
    @PostMapping("/password/reset") public Map<String,String> reset(@RequestBody Reset i,HttpServletRequest r) {
        rate.limit("RESET_APPLY_IP",r.getRemoteAddr(),20,60);
        recovery.reset(i.token(),i.password(),i.confirmation());
        return Map.of("next","/login?passwordChanged");
    }
}
