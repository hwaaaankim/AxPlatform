package com.axplatform.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class PasswordRules {
    private PasswordRules() { }

    public static Optional<String> validateNew(String password, String confirmation) {
        if (password == null || password.length() < 12 || password.length() > 64) {
            return Optional.of("새 비밀번호는 12~64자로 입력해 주세요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.of("비밀번호의 UTF-8 길이가 72바이트를 넘습니다. 조금 짧게 입력해 주세요.");
        }
        if (!password.equals(confirmation)) {
            return Optional.of("새 비밀번호와 확인 값이 일치하지 않습니다.");
        }
        return Optional.empty();
    }
}
