package com.axplatform.web;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class ApiErrorWriter {
    private ApiErrorWriter() { }
    public static boolean isAsync(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/fragments/");
    }
    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\"}");
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
