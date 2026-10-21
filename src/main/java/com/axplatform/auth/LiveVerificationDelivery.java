package com.axplatform.auth;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.axplatform.common.*;
import com.axplatform.security.Crypto;
import org.springframework.stereotype.Component;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
@Component public class LiveVerificationDelivery implements VerificationDelivery {
    private final AuthSettings cfg;
    private final JsonCodec json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public LiveVerificationDelivery(AuthSettings cfg,JsonCodec json) {
        this.cfg=cfg;
        this.json=json;
    }
    @Override public boolean emailReady() {
        return cfg.enabled("ax.integrations.smtp.enabled")&&cfg.supplied("ax.integrations.smtp.host")&&cfg.supplied("ax.integrations.smtp.from")&&(!Boolean.parseBoolean(cfg.value("ax.integrations.smtp.auth","true"))||(cfg.supplied("ax.integrations.smtp.username")&&cfg.supplied("ax.integrations.smtp.password")));
    }
    @Override public boolean smsReady() {
        return cfg.enabled("ax.integrations.solapi.enabled")&&cfg.supplied("ax.integrations.solapi.api-key")&&cfg.supplied("ax.integrations.solapi.api-secret")&&cfg.supplied("ax.integrations.solapi.sender");
    }
    @Override public void email(String target,String subject,String body) {
        if(!emailReady())throw new Problem(503,"EMAIL_NOT_CONNECTED","이메일 인증용 SMTP 연결을 먼저 설정해 주세요.");
        try {
            var mail=new JavaMailSenderImpl();
            mail.setHost(cfg.value("ax.integrations.smtp.host",""));
            mail.setPort(cfg.integer("ax.integrations.smtp.port",587));
            mail.setUsername(cfg.value("ax.integrations.smtp.username",""));
            mail.setPassword(cfg.value("ax.integrations.smtp.password",""));
            mail.setDefaultEncoding("UTF-8");
            var props=mail.getJavaMailProperties();
            props.setProperty("mail.smtp.auth",String.valueOf(Boolean.parseBoolean(cfg.value("ax.integrations.smtp.auth","true"))));
            props.setProperty("mail.smtp.starttls.enable",cfg.value("ax.integrations.smtp.starttls","true"));
            props.setProperty("mail.smtp.starttls.required",cfg.value("ax.integrations.smtp.starttls-required",cfg.value("ax.integrations.smtp.starttls","true")));
            props.setProperty("mail.smtp.ssl.checkserveridentity","true");
            props.setProperty("mail.smtp.ssl.enable",cfg.value("ax.integrations.smtp.ssl","false"));
            props.setProperty("mail.smtp.connectiontimeout","5000");
            props.setProperty("mail.smtp.timeout","10000");
            props.setProperty("mail.smtp.writetimeout","10000");
            var message=new SimpleMailMessage();
            message.setFrom(Inputs.email(cfg.value("ax.integrations.smtp.from","")));
            message.setTo(Inputs.email(target));
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
        }
        catch(RuntimeException e) {
            throw new Problem(503,"EMAIL_DELIVERY_FAILED","메일 요청을 완료하지 못했습니다. SMTP 설정과 발신 주소를 확인해 주세요.");
        }
    }
    @Override public void sms(String phone,String body) {
        if(!smsReady())throw new Problem(503,"SMS_NOT_CONNECTED","문자 인증용 SOLAPI 연결을 먼저 설정해 주세요.");
        String sender=cfg.value("ax.integrations.solapi.sender","").replaceAll("[ -]","");
        if(!sender.matches("[0-9]{8,11}"))throw new Problem(503,"SMS_SENDER_INVALID","사전 등록한 발신번호를 숫자로 설정해 주세요.");
        if(body.getBytes(StandardCharsets.UTF_8).length>1600)throw new Problem(503,"SMS_BODY_LIMIT","문자 내용이 발송 한도를 초과했습니다.");
        String date=Instant.now().toString(),salt=UUID.randomUUID().toString().replace("-","");
        String header="HMAC-SHA256 apiKey="+cfg.value("ax.integrations.solapi.api-key","")+", date="+date+", salt="+salt+", signature="+Crypto.hmac(cfg.value("ax.integrations.solapi.api-secret",""),date+salt);
        try {
            String target="0"+Inputs.phone(phone).substring(3);
            var data=Map.of("messages",List.of(Map.of("to",target,"from",sender,"text",body,"type",body.getBytes(StandardCharsets.UTF_8).length>90?"LMS":"SMS","country","82")));
            var request=HttpRequest.newBuilder(URI.create("https://api.solapi.com/messages/v4/send-many/detail")).timeout(Duration.ofSeconds(10)).header("Authorization",header).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.write(data))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300||response.body().length()>200000)throw new IllegalStateException();
            var result=json.object(response.body());
            if(result.get("failedMessageList") instanceof List<?> list&&!list.isEmpty())throw new IllegalStateException();
            var info=result.get("groupInfo");
            if(!(info instanceof Map<?,?> group)||!(group.get("count") instanceof Map<?,?> count)||!(count.get("registeredSuccess") instanceof Number n)||n.intValue()!=1)throw new IllegalStateException();
        }
        catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new Problem(503,"SMS_DELIVERY_FAILED","문자 접수 결과를 확인하지 못했습니다. 발신번호·잔액·API 설정을 확인해 주세요.");
        }
    }
}
