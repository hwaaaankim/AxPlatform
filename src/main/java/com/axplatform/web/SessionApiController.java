package com.axplatform.web;

import java.util.*;
import com.axplatform.security.*;
import com.axplatform.workspace.SpaceService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController public class SessionApiController {
    private final SpaceService spaces;
    public SessionApiController(SpaceService spaces) {
        this.spaces=spaces;
    }
    @GetMapping("/api/session/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName",token.getHeaderName(),"parameterName",token.getParameterName(),"token",token.getToken());
    }
    @GetMapping("/api/me") public Map<String,Object> me() {
        SessionSubject s=Subjects.current();
        var out=new LinkedHashMap<String,Object>();
        out.put("accountId",s.accountId());
        out.put("accountType",s.administrator()?"SYSTEM_ADMIN":"CUSTOMER");
        out.put("systemRole",s.administrator()?"SUPER_ADMIN":"CUSTOMER");
        out.put("stage","P1+P2_PARTIAL");
        out.put("workspaces",spaces.spaces(s));
        if(s instanceof CustomerPrincipal p) {
            out.put("loginMethod",p.loginMethod());
            out.put("displayName",p.displayName());
        }
        else if(s instanceof AdminPrincipal p)out.put("username",p.username());
        return out;
    }
}
