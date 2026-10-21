package com.axplatform.auth;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import com.axplatform.common.*;
import com.axplatform.security.Crypto;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class VerificationService {
    public record Receipt(String id,int expiresIn,int resendAfter) {
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final AuthSettings cfg;
    private final VerificationDelivery delivery;
    private final AuthRateService rate;
    private final Clock clock;
    public VerificationService(JdbcTemplate db,TransactionTemplate tx,AuthSettings cfg,VerificationDelivery delivery,AuthRateService rate,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.cfg=cfg;
        this.delivery=delivery;
        this.rate=rate;
        this.clock=clock;
    }
    public String session(HttpSession session) {
        synchronized(session) {
            Object value=session.getAttribute("SIGNUP_BINDING");
            if(value==null) {
                value=Crypto.token();
                session.setAttribute("SIGNUP_BINDING",value);
            }
            return Crypto.hmac(cfg.secret(),(String)value);
        }
    }
    public Receipt issue(String purpose,String raw,HttpSession session,String ip) {
        if(!cfg.registration())throw new Problem(503,"REGISTRATION_DISABLED","회원가입 연결 준비 중입니다.");
        Inputs.state(purpose,Set.of("SIGNUP_EMAIL","SIGNUP_PHONE"));
        String target=purpose.equals("SIGNUP_EMAIL")?Inputs.email(raw):Inputs.phone(raw),binding=session(session);
        cfg.secret();
        if(purpose.equals("SIGNUP_EMAIL")&&!delivery.emailReady())throw new Problem(503,"EMAIL_NOT_CONNECTED","이메일 인증 연결을 준비해 주세요.");
        if(purpose.equals("SIGNUP_PHONE")&&!delivery.smsReady())throw new Problem(503,"SMS_NOT_CONNECTED","문자 인증 연결을 준비해 주세요.");
        rate.limit("OTP_IP",ip,30,60);
        rate.limit("OTP_TARGET",Crypto.hmac(cfg.secret(),target),20,1440);
        rate.limit("OTP_SESSION",binding,20,60);
        String id=UUID.randomUUID().toString(),code=Crypto.otp();
        tx.executeWithoutResult(t-> {
            var now=LocalDateTime.now(clock);
            String lockKey=Crypto.sha256("OTP_LOCK|"+binding+"|"+purpose);
            db.update("INSERT INTO ap_auth_rate(key_hash,attempts,window_start) VALUES(?,0,?) ON DUPLICATE KEY UPDATE key_hash=key_hash",lockKey,now);
            db.queryForMap("SELECT key_hash FROM ap_auth_rate WHERE key_hash=? FOR UPDATE",lockKey);
            var previous=db.queryForList("SELECT created_at FROM ap_verification WHERE session_hash=? AND purpose=? AND target=? ORDER BY created_at DESC LIMIT 1 FOR UPDATE",binding,purpose,target);
            if(!previous.isEmpty()&&((java.sql.Timestamp)previous.get(0).get("created_at")).toLocalDateTime().plusSeconds(60).isAfter(now))throw new Problem(429,"OTP_RESEND_WAIT","60초 후 다시 요청해 주세요.");
            db.update("UPDATE ap_verification SET status='REPLACED' WHERE session_hash=? AND purpose=? AND consumed_at IS NULL",binding,purpose);
            db.update("INSERT INTO ap_verification(id,purpose,target,session_hash,code_mac,status,expires_at,created_at) VALUES(?,?,?,?,?,'ISSUED',?,?)",id,purpose,target,binding,Crypto.hmac(cfg.secret(),id+"|"+purpose+"|"+target+"|"+binding+"|"+code),now.plusMinutes(5),now);
        }
        );
        try {
            if(purpose.equals("SIGNUP_EMAIL"))delivery.email(target,"AxPlatform 이메일 인증","인증번호: "+code+"\n5분 안에 입력해 주세요.");
            else delivery.sms(target,"AxPlatform 인증번호: "+code+" (5분)");
        }
        catch(Problem p) {
            db.update("UPDATE ap_verification SET status='FAILED' WHERE id=?",id);
            throw p;
        }
        return new Receipt(id,300,60);
    }
    public boolean verify(String id,String code,HttpSession session) {
        if(code==null||!code.matches("[0-9]{6}"))throw Problem.invalid("6자리 인증번호를 입력해 주세요.");
        String binding=session(session);
        Boolean ok=tx.execute(t-> {
            var rows=db.queryForList("SELECT * FROM ap_verification WHERE id=? FOR UPDATE",id);
            if(rows.size()!=1)return false;
            var r=rows.get(0);
            var now=LocalDateTime.now(clock);
            if(!binding.equals(r.get("session_hash"))||!"ISSUED".equals(r.get("status"))||!((java.sql.Timestamp)r.get("expires_at")).toLocalDateTime().isAfter(now))return false;
            int tries=((Number)r.get("attempts")).intValue()+1;
            String expected=Crypto.hmac(cfg.secret(),id+"|"+r.get("purpose")+"|"+r.get("target")+"|"+binding+"|"+code);
            if(!Crypto.equal(expected,(String)r.get("code_mac"))) {
                db.update("UPDATE ap_verification SET attempts=?,status=? WHERE id=?",tries,tries>=5?"LOCKED":"ISSUED",id);
                return false;
            }
            db.update("UPDATE ap_verification SET verified_at=?,proof_expires_at=?,status='VERIFIED' WHERE id=?",now,now.plusMinutes(30),id);
            return true;
        }
        );
        return Boolean.TRUE.equals(ok);
    }
    /** Called inside the account-creation transaction. Lock, target, binding, expiry and single consumption all required. */
    public void consume(String id,String purpose,String target,HttpSession session) {
        var rows=db.queryForList("SELECT * FROM ap_verification WHERE id=? FOR UPDATE",id);
        var now=LocalDateTime.now(clock);
        if(rows.size()!=1)throw new Problem(422,"VERIFICATION_REQUIRED","인증을 먼저 완료해 주세요.");
        var r=rows.get(0);
        if(!purpose.equals(r.get("purpose"))||!target.equals(r.get("target"))||!session(session).equals(r.get("session_hash"))||!"VERIFIED".equals(r.get("status"))||r.get("proof_expires_at")==null||!((java.sql.Timestamp)r.get("proof_expires_at")).toLocalDateTime().isAfter(now))throw new Problem(422,"VERIFICATION_REQUIRED","이메일·전화번호 인증이 변경되거나 만료되었습니다.");
        db.update("UPDATE ap_verification SET consumed_at=?,status='CONSUMED' WHERE id=?",now,id);
    }
}
