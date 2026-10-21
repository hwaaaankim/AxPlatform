package com.axplatform.workspace;

import java.time.*;
import java.util.*;
import com.axplatform.security.*;
import com.axplatform.policy.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.HttpSession;
@Service public class PreferenceService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final Clock clock;
    public PreferenceService(JdbcTemplate db,AccessService access,Clock clock) {
        this.db=db;
        this.access=access;
        this.clock=clock;
    }
    public boolean showPlan(SessionSubject s,long workspace,HttpSession session) {
        if(s.administrator())return false;
        access.member(s,workspace);
        var plans=db.queryForList("SELECT plan_code FROM ap_workspace WHERE id=? AND owner_account_id=?",String.class,workspace,s.accountId());
        if(plans.isEmpty()||!"FREE".equals(plans.get(0)))return false;
        var hidden=db.queryForList("SELECT plan_hide_until FROM ap_account_preference WHERE account_id=?",s.accountId());
        if(!hidden.isEmpty()&&hidden.get(0).get("plan_hide_until") instanceof java.sql.Timestamp t&&t.toLocalDateTime().isAfter(LocalDateTime.now(clock)))return false;
        synchronized(session) {
            if(Boolean.TRUE.equals(session.getAttribute("PLAN_POPUP_SEEN")))return false;
            session.setAttribute("PLAN_POPUP_SEEN",true);
            return true;
        }
    }
    public void hide(SessionSubject s) {
        if(s.administrator())return;
        var until=LocalDateTime.now(clock).plusDays(7);
        db.update("INSERT INTO ap_account_preference(account_id,plan_hide_until) VALUES(?,?) ON DUPLICATE KEY UPDATE plan_hide_until=VALUES(plan_hide_until)",s.accountId(),until);
    }
}
