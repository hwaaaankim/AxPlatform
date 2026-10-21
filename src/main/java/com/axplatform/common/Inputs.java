package com.axplatform.common;

import java.util.*;
public final class Inputs {
    private Inputs() {
    }
    public static String text(String value,int max,String label) {
        if(value==null||value.strip().isEmpty()||value.strip().length()>max||value.indexOf('\0')>=0)throw Problem.invalid(label+"을 확인해 주세요.");
        return value.strip();
    }
    public static String email(String value) {
        String s=text(value,254,"이메일").toLowerCase(Locale.ROOT);
        if(!s.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}")||s.contains("..")||s.startsWith(".")||s.contains(".@"))throw Problem.invalid("이메일 형식을 확인해 주세요.");
        return s;
    }
    public static String phone(String value) {
        String s=text(value,30,"휴대전화").replaceAll("[ ()-]","");
        if(s.matches("010[0-9]{8}"))return "+82"+s.substring(1);
        if(s.matches("8210[0-9]{8}"))s="+"+s;
        if(!s.matches("\\+8210[0-9]{8}"))throw Problem.invalid("문자 인증은 국내 휴대전화 번호(010 또는 +82)를 입력해 주세요.");
        return s;
    }
    public static String code(String s) {
        s=text(s,100,"기능 코드");
        if(!s.matches("[a-z][a-z0-9_.-]{2,99}"))throw Problem.invalid("코드는 소문자 영문·숫자·점·밑줄·하이픈으로 입력해 주세요.");
        return s;
    }
    public static String state(String v,Set<String> allowed) {
        if(v==null||!allowed.contains(v))throw Problem.invalid("선택값을 확인해 주세요.");
        return v;
    }
    public static long number(Map<String,Object> row,String key) {
        return ((Number)row.get(key)).longValue();
    }
}
