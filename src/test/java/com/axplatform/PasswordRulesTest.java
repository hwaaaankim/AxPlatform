package com.axplatform;

import com.axplatform.security.PasswordRules;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordRulesTest {
    @Test void koreanPasswordsRespectBcryptByteLimit() {
        String allowed = "가".repeat(24);
        String tooLong = "가".repeat(25);
        assertThat(PasswordRules.validateNew(allowed, allowed)).isEmpty();
        assertThat(PasswordRules.validateNew(tooLong, tooLong)).isPresent();
    }
    @Test void doesNotAllowTheBootstrapPasswordForLaterChanges() {
        assertThat(PasswordRules.validateNew("12345", "12345")).isPresent();
    }
}
