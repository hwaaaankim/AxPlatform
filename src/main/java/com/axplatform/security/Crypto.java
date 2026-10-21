package com.axplatform.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class Crypto {
    private static final SecureRandom RANDOM=new SecureRandom();
    private Crypto() {
    }
    public static String token() {
        byte[] b=new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public static String otp() {
        return String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000));
    }
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch(GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
    public static String hmac(String secret,String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch(GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
    public static boolean equal(String a,String b) {
        return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));
    }
}
