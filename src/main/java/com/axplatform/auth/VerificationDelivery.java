package com.axplatform.auth;
public interface VerificationDelivery {boolean emailReady();boolean smsReady();void email(String target,String subject,String body);void sms(String phone,String body);}
