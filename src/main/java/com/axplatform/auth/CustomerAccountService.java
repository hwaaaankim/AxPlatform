package com.axplatform.auth;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import com.axplatform.common.*;
import com.axplatform.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class CustomerAccountService {
    public record Signup(String email,String password,String confirmation,String name,String phone,String emailProof,String phoneProof,boolean terms,boolean privacy) {
    }
    public record SocialPending(String provider,String subject,String email,boolean emailVerified,String name,String phone,Instant expires) implements java.io.Serializable {
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final AuthSettings cfg;
    private final VerificationService proof;
    private final PasswordEncoder encoder;
    private final AdminCredentialStore keys;
    private final Clock clock;
    private final String dummy;
    public CustomerAccountService(JdbcTemplate db,TransactionTemplate tx,AuthSettings cfg,VerificationService proof,PasswordEncoder encoder,AdminCredentialStore keys,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.cfg=cfg;
        this.proof=proof;
        this.encoder=encoder;
        this.keys=keys;
        this.clock=clock;
        this.dummy=encoder.encode(Crypto.token());
    }
    public CustomerPrincipal signup(Signup in,HttpSession session) {
        if(!cfg.registration())throw new Problem(503,"REGISTRATION_DISABLED","가입 연결 준비 중입니다.");
        String email=Inputs.email(in.email()),phone=Inputs.phone(in.phone()),name=Inputs.text(in.name(),100,"이름");
        if(!in.terms()||!in.privacy())throw Problem.invalid("필수 약관을 확인하고 동의해 주세요.");
        Object pendingValue=session.getAttribute("SOCIAL_PENDING");
        SocialPending social=pendingValue instanceof SocialPending p?p:null;
        if(social!=null&&!social.expires().isAfter(Instant.now(clock)))throw new Problem(401,"SOCIAL_EXPIRED","SNS 인증이 만료되었습니다. 다시 시작해 주세요.");
        String hash=null;
        if(social==null) {
            var invalid=PasswordRules.validateNew(in.password(),in.confirmation());
            if(invalid.isPresent())throw Problem.invalid(invalid.get());
            hash=encoder.encode(in.password());
        }
        final String passwordHash=hash;
        try {
            return tx.execute(t-> {
                if(!(social!=null&&social.emailVerified()&&email.equals(social.email())))proof.consume(in.emailProof(),"SIGNUP_EMAIL",email,session);
                proof.consume(in.phoneProof(),"SIGNUP_PHONE",phone,session);
                var now=LocalDateTime.now(clock);
                long account=keys.insert("INSERT INTO ap_account(account_type,system_role,status,display_name,contact_email,phone_e164,created_at,updated_at,version,security_version) VALUES('CUSTOMER','CUSTOMER','ACTIVE',?,?,?,?,?,0,0)",name,email,phone,now,now);
                String method;
                if(social==null) {
                    db.update("INSERT INTO ap_email_credential(account_id,login_email,password_hash,email_verified_at,security_version) VALUES(?,?,?,?,0)",account,email,passwordHash,now);
                    method="EMAIL";
                }
                else {
                    db.update("INSERT INTO ap_external_identity(account_id,provider,provider_subject) VALUES(?,?,?)",account,social.provider(),social.subject());
                    method=social.provider();
                }
                long workspace=keys.insert("INSERT INTO ap_workspace(owner_account_id,name,kind,plan_code,created_at,updated_at,version) VALUES(?,?,'CUSTOMER','FREE',?,?,0)",account,name.substring(0,Math.min(name.length(),85))+"의 업무 공간",now,now);
                db.update("INSERT INTO ap_workspace_member(workspace_id,account_id,member_role,created_at) VALUES(?,?,'OWNER',?)",workspace,account,now);
                db.update("INSERT INTO ap_business_scope(workspace_id,name,is_default,created_at,updated_at,version) VALUES(?,'기본 사업',TRUE,?,?,0)",workspace,now,now);
                db.update("INSERT INTO ap_consent(account_id,terms_version,privacy_version,agreed_at) VALUES(?,?,?,?)",account,cfg.value("ax.auth.terms-version","2026-10-09-DRAFT"),cfg.value("ax.auth.privacy-version","2026-10-09-DRAFT"),now);
                db.update("INSERT INTO ap_account_preference(account_id) VALUES(?)",account);
                return new CustomerPrincipal(account,0,0,method,name);
            }
            );
        }
        catch(org.springframework.dao.DuplicateKeyException duplicate) {
            throw new Problem(409,"SIGNUP_CONFLICT","이미 등록된 가입 방식이거나 가입이 처리되었습니다. 로그인 화면에서 확인해 주세요.");
        }
    }
    public Optional<CustomerPrincipal> authenticate(String raw,String password) {
        String email;
        try {
            email=Inputs.email(raw);
        }
        catch(Problem p) {
            return Optional.empty();
        }
        if(password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)return Optional.empty();
        return tx.execute(t-> {
            var rows=db.queryForList("SELECT e.*,a.display_name,a.account_type,a.status,a.security_version AS account_version FROM ap_email_credential e JOIN ap_account a ON a.id=e.account_id WHERE e.login_email=? FOR UPDATE",email);
            var now=LocalDateTime.now(clock);
            if(rows.isEmpty()) {
                encoder.matches(password,dummy);
                return Optional.empty();
            }
            var c=rows.get(0);
            if(!"CUSTOMER".equals(c.get("account_type"))||!"ACTIVE".equals(c.get("status"))) {
                encoder.matches(password,dummy);
                return Optional.empty();
            }
            var locked=c.get("locked_until");
            if(locked instanceof java.sql.Timestamp ts&&ts.toLocalDateTime().isAfter(now))return Optional.empty();
            if(!encoder.matches(password,(String)c.get("password_hash"))) {
                int failures=locked==null?((Number)c.get("failed_attempts")).intValue():0;
                failures++;
                db.update("UPDATE ap_email_credential SET failed_attempts=?,locked_until=? WHERE account_id=?",failures,failures>=5?now.plusMinutes(15):null,c.get("account_id"));
                return Optional.empty();
            }
            db.update("UPDATE ap_email_credential SET failed_attempts=0,locked_until=NULL WHERE account_id=?",c.get("account_id"));
            return Optional.of(new CustomerPrincipal(Inputs.number(c,"account_id"),Inputs.number(c,"account_version"),Inputs.number(c,"security_version"),"EMAIL",(String)c.get("display_name")));
        }
        );
    }
    public Optional<CustomerPrincipal> social(String provider,String subject) {
        var rows=db.queryForList("SELECT a.id,a.display_name,a.security_version FROM ap_external_identity e JOIN ap_account a ON a.id=e.account_id WHERE e.provider=? AND e.provider_subject=? AND a.account_type='CUSTOMER' AND a.status='ACTIVE'",provider,subject);
        if(rows.isEmpty())return Optional.empty();
        var a=rows.get(0);
        return Optional.of(new CustomerPrincipal(Inputs.number(a,"id"),Inputs.number(a,"security_version"),0,provider,(String)a.get("display_name")));
    }
    public boolean socialExists(String provider,String subject) {
        return db.queryForObject("SELECT COUNT(*) FROM ap_external_identity WHERE provider=? AND provider_subject=?",Integer.class,provider,subject)>0;
    }
}
