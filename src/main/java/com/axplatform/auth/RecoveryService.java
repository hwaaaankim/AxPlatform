package com.axplatform.auth;

import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.policy.PolicyService;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class RecoveryService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final VerificationDelivery delivery;
    private final AuthSettings cfg;
    private final AuthRateService rate;
    private final PasswordEncoder encoder;
    private final PolicyService policies;
    private final Clock clock;
    public RecoveryService(JdbcTemplate db,TransactionTemplate tx,VerificationDelivery delivery,AuthSettings cfg,AuthRateService rate,PasswordEncoder encoder,PolicyService policies,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.delivery=delivery;
        this.cfg=cfg;
        this.rate=rate;
        this.encoder=encoder;
        this.policies=policies;
        this.clock=clock;
    }
    public void findId(String input,String ip) {
        String phone=Inputs.phone(input);
        if(!delivery.smsReady())throw new Problem(503,"SMS_NOT_CONNECTED","문자 인증 연결 준비 중입니다.");
        rate.limit("RECOVERY_IP",ip,10,60);
        rate.limit("FIND_TARGET",Crypto.hmac(cfg.secret(),phone),3,60);
        var emails=db.queryForList("SELECT e.login_email FROM ap_email_credential e JOIN ap_account a ON a.id=e.account_id WHERE a.account_type='CUSTOMER' AND a.status='ACTIVE' AND a.phone_e164=? ORDER BY e.login_email",String.class,phone);
        if(!emails.isEmpty()) {
            try {
                delivery.sms(phone,"AxPlatform 이메일 로그인 아이디: "+String.join(", ",emails));
            }
            catch(Problem p) {
                audit("RECOVERY_DELIVERY_FAILED");
            }
        }
    }
    public void requestReset(String rawEmail,String rawPhone,String ip) {
        String email=Inputs.email(rawEmail),phone=Inputs.phone(rawPhone);
        if(!delivery.emailReady())throw new Problem(503,"EMAIL_NOT_CONNECTED","이메일 인증 연결 준비 중입니다.");
        rate.limit("RECOVERY_IP",ip,10,60);
        rate.limit("RESET_TARGET",Crypto.hmac(cfg.secret(),email+"|"+phone),3,60);
        String base=cfg.base();
        var ids=db.queryForList("SELECT a.id FROM ap_email_credential e JOIN ap_account a ON a.id=e.account_id WHERE e.login_email=? AND a.phone_e164=? AND a.account_type='CUSTOMER' AND a.status='ACTIVE'",Long.class,email,phone);
        if(ids.isEmpty())return;
        String token=Crypto.token(),hash=Crypto.sha256(token);
        var now=LocalDateTime.now(clock);
        db.update("INSERT INTO ap_password_reset(token_hash,account_id,expires_at,created_at) VALUES(?,?,?,?)",hash,ids.get(0),now.plusMinutes(15),now);
        try {
            delivery.email(email,"AxPlatform 비밀번호 재설정", "15분 안에 아래 링크를 열어 새 비밀번호를 설정해 주세요.\n"+base+"/password/reset?token="+token+"\n요청하지 않았다면 이 메일을 무시하세요.");
        }
        catch(Problem p) {
            db.update("DELETE FROM ap_password_reset WHERE token_hash=?",hash);
            audit("RECOVERY_DELIVERY_FAILED");
        }
    }
    public void reset(String token,String next,String confirmation) {
        var invalid=PasswordRules.validateNew(next,confirmation);
        if(invalid.isPresent())throw Problem.invalid(invalid.get());
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new Problem(422,"RESET_EXPIRED","링크가 만료되거나 올바르지 않습니다.");
        String hash=Crypto.sha256(token),passwordHash=encoder.encode(next);
        tx.executeWithoutResult(t-> {
            policies.state(true);
            var rows=db.queryForList("SELECT r.*,a.account_type,a.status FROM ap_password_reset r JOIN ap_account a ON a.id=r.account_id WHERE r.token_hash=? FOR UPDATE",hash);
            var now=LocalDateTime.now(clock);
            if(rows.size()!=1||rows.get(0).get("consumed_at")!=null||!"CUSTOMER".equals(rows.get(0).get("account_type"))||!"ACTIVE".equals(rows.get(0).get("status"))||!((java.sql.Timestamp)rows.get(0).get("expires_at")).toLocalDateTime().isAfter(now))throw new Problem(422,"RESET_EXPIRED","링크가 만료되었거나 사용되었습니다. 다시 요청해 주세요.");
            long account=Inputs.number(rows.get(0),"account_id");
            int updated=db.update("UPDATE ap_email_credential SET password_hash=?,security_version=security_version+1,failed_attempts=0,locked_until=NULL WHERE account_id=?",passwordHash,account);
            if(updated!=1)throw new Problem(422,"RESET_EXPIRED","이메일 계정을 확인해 주세요.");
            db.update("UPDATE ap_account SET security_version=security_version+1 WHERE id=?",account);
            db.update("UPDATE ap_password_reset SET consumed_at=? WHERE account_id=? AND consumed_at IS NULL",now,account);
            db.update("INSERT INTO ap_admin_audit(actor_account_id,action,detail,created_at) VALUES(?,'CUSTOMER_PASSWORD_RESET','이메일 계정 암호 변경 및 이전 세션 철회',?)",account,now);
        }
        );
    }
    private void audit(String action) {
        db.update("INSERT INTO ap_admin_audit(action,detail,created_at) VALUES(?,'복구 요청의 공급자 접수 결과 확인 필요',?)",action,LocalDateTime.now(clock));
    }
}
