package com.axplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AxPlatformApplication {
    public static void main(String[] args) {
        // DATETIME columns and authentication expiry are stored in UTC on every PC.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AxPlatformApplication.class, args);
    }
}
