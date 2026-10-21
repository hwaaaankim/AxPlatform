package com.axplatform.web;

import java.util.*;
import com.axplatform.auth.*;
import org.springframework.web.bind.annotation.*;
@ControllerAdvice public class SharedModel {
    private final AuthSettings cfg;
    private final VerificationDelivery delivery;
    public SharedModel(AuthSettings cfg,VerificationDelivery delivery) {
        this.cfg=cfg;
        this.delivery=delivery;
    }
    @ModelAttribute("connections") public Map<String,Object> connections() {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("registration",cfg.registration());
        m.put("email",delivery.emailReady());
        m.put("sms",delivery.smsReady());
        for(String name:List.of("google","kakao","naver"))m.put(name,cfg.enabled("ax.integrations."+name+"-login.enabled"));
        return m;
    }
    @ModelAttribute("appName") public String name() {
        return cfg.value("ax.display-name","AxPlatform");
    }
}
