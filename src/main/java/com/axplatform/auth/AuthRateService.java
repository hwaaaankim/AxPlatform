package com.axplatform.auth;

import java.time.*;
import com.axplatform.common.Problem;
import com.axplatform.security.Crypto;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class AuthRateService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Clock clock;
    public AuthRateService(JdbcTemplate db,TransactionTemplate tx,Clock clock) {
        this.db=db;
        this.tx=tx;
        this.clock=clock;
    }
    public void limit(String category,String value,int maximum,int minutes) {
        String key=Crypto.sha256(category+"|"+value);
        Boolean accepted=tx.execute(t-> {
            var now=LocalDateTime.now(clock);
            db.update("INSERT INTO ap_auth_rate(key_hash,attempts,window_start) VALUES(?,0,?) ON DUPLICATE KEY UPDATE key_hash=key_hash",key,now);
            var r=db.queryForMap("SELECT attempts,window_start FROM ap_auth_rate WHERE key_hash=? FOR UPDATE",key);
            var start=((java.sql.Timestamp)r.get("window_start")).toLocalDateTime();
            int attempts=((Number)r.get("attempts")).intValue();
            if(!start.plusMinutes(minutes).isAfter(now)) {
                attempts=0;
                start=now;
            }
            if(attempts>=maximum)return false;
            db.update("UPDATE ap_auth_rate SET attempts=?,window_start=? WHERE key_hash=?",attempts+1,start,key);
            return true;
        }
        );
        if(!Boolean.TRUE.equals(accepted))throw new Problem(429,"AUTH_RATE_LIMIT","인증 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }
}
