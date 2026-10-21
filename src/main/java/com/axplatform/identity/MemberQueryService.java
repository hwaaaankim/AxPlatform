package com.axplatform.identity;

import java.time.*;
import java.util.*;
import com.axplatform.common.Inputs;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
@Service @PreAuthorize("hasRole('SUPER_ADMIN')") public class MemberQueryService {
    public record Member(long id,String name,String email,String phone,String status,LocalDateTime createdAt,String method,String type,long version) {
    }
    public record Page(List<Member> members,long total,int number,int totalPages,String query,String status,String method,String type) {
    }
    private final JdbcTemplate db;
    public MemberQueryService(JdbcTemplate db) {
        this.db=db;
    }
    public Page search(String raw,int page) {
        return search(raw,page,"ALL","ALL","CUSTOMER");
    }
    public Page search(String raw,int requested,String status,String method,String type) {
        Inputs.state(status,Set.of("ALL","ACTIVE","SUSPENDED","WITHDRAWN"));
        Inputs.state(method,Set.of("ALL","EMAIL","GOOGLE","KAKAO","NAVER"));
        Inputs.state(type,Set.of("CUSTOMER","SYSTEM_ADMIN"));
        String query=raw==null?"":raw.strip();
        if(query.length()>100)query=query.substring(0,100);
        String like="%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var args=new ArrayList<Object>();
        String where="a.account_type=? AND (a.display_name LIKE ? ESCAPE '!' OR a.contact_email LIKE ? ESCAPE '!' OR a.phone_e164 LIKE ? ESCAPE '!')";
        args.add(type);
        args.add(like);
        args.add(like);
        args.add(like);
        if(!"ALL".equals(status)) {
            where+=" AND a.status=?";
            args.add(status);
        }
        if("EMAIL".equals(method))where+=" AND e.account_id IS NOT NULL";
        else if(!"ALL".equals(method)) {
            where+=" AND x.provider=?";
            args.add(method);
        }
        String from=" FROM ap_account a LEFT JOIN ap_email_credential e ON e.account_id=a.id LEFT JOIN ap_external_identity x ON x.account_id=a.id WHERE "+where;
        long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        int pages=Math.max(1,(int)Math.min(Integer.MAX_VALUE,(total+24)/25)),page=Math.max(1,Math.min(requested,pages));
        var params=new ArrayList<>(args);
        params.add((page-1)*25);
        var members=db.query("SELECT a.id,a.display_name,a.contact_email,a.phone_e164,a.status,a.created_at,a.account_type,a.version,CASE WHEN e.account_id IS NOT NULL THEN 'EMAIL' WHEN x.provider IS NOT NULL THEN x.provider ELSE 'ADMIN' END AS method"+from+" ORDER BY a.id DESC LIMIT 25 OFFSET ?",(rs,n)->new Member(rs.getLong("id"),rs.getString("display_name"),rs.getString("contact_email"),rs.getString("phone_e164"),rs.getString("status"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getString("method"),rs.getString("account_type"),rs.getLong("version")),params.toArray());
        return new Page(members,total,page,pages,query,status,method,type);
    }
}
