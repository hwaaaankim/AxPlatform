package com.axplatform.security;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
@Service public class AccountSessionService {
    private final JdbcTemplate db;
    private final AdminAuthenticationService admins;
    public AccountSessionService(JdbcTemplate db,AdminAuthenticationService admins) {
        this.db=db;
        this.admins=admins;
    }
    public boolean current(SessionSubject s) {
        if(s instanceof AdminPrincipal a)return admins.isCurrent(a);
        if(!(s instanceof CustomerPrincipal p))return false;
        var rows=db.queryForList("SELECT account_type,system_role,status,security_version FROM ap_account WHERE id=?",p.accountId());
        if(rows.size()!=1)return false;
        var r=rows.get(0);
        if(!"CUSTOMER".equals(r.get("account_type"))||!"CUSTOMER".equals(r.get("system_role"))||!"ACTIVE".equals(r.get("status"))||((Number)r.get("security_version")).longValue()!=p.accountVersion())return false;
        if("EMAIL".equals(p.loginMethod())) {
            var versions=db.queryForList("SELECT security_version FROM ap_email_credential WHERE account_id=?",Long.class,p.accountId());
            return versions.size()==1&&versions.get(0)==p.credentialVersion();
        }
        return Set.of("GOOGLE","KAKAO","NAVER").contains(p.loginMethod())&&db.queryForObject("SELECT COUNT(*) FROM ap_external_identity WHERE account_id=? AND provider=?",Integer.class,p.accountId(),p.loginMethod())==1;
    }
}
