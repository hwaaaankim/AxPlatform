package com.axplatform.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminAuthenticationService {
    private final com.axplatform.operations.ActivityRecorder activity;
    private final AdminCredentialStore store;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String dummyHash;

    public AdminAuthenticationService(
            AdminCredentialStore store,
            PasswordEncoder encoder,
            TransactionTemplate transactions,
            Clock clock,
            com.axplatform.operations.ActivityRecorder activity) {
        this.activity = activity;
        this.store = store;
        this.encoder = encoder;
        this.transactions = transactions;
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public Optional<AdminPrincipal> authenticate(String username, String password) {
        if (username == null
                || username.length() > 100
                || password == null
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.empty();
        }
        // Returning a failure result commits failure counters. Throwing inside this transaction
        // would roll them back.
        Optional<AdminPrincipal> result =
                transactions.execute(
                        tx -> {
                            var found = store.lockByUsername(username);
                            LocalDateTime now = LocalDateTime.now(clock);
                            if (found.isEmpty()) {
                                encoder.matches(password, dummyHash);
                                return Optional.empty();
                            }
                            var credential = found.get();
                            if (!credential.eligible()) {
                                encoder.matches(password, dummyHash);
                                return Optional.empty();
                            }
                            if (credential.lockedUntil() != null
                                    && credential.lockedUntil().isAfter(now)) {
                                return Optional.empty();
                            }
                            if (!encoder.matches(password, credential.hash())) {
                                int previous =
                                        credential.lockedUntil() != null
                                                ? 0
                                                : credential.failures();
                                int count = previous + 1;
                                store.failures(
                                        credential.id(),
                                        count,
                                        count >= 5 ? now.plusMinutes(15) : null,
                                        now);
                                return Optional.empty();
                            }
                            store.failures(credential.id(), 0, null, now);
                            store.audit(credential.accountId(), "ADMIN_LOGIN", "관리자 인증 성공", now);
                            return Optional.of(
                                    new AdminPrincipal(
                                            credential.accountId(),
                                            credential.id(),
                                            credential.securityVersion(),
                                            credential.username()));
                        });
        activity.login(
                result != null && result.isPresent() ? result.get().accountId() : null,
                result != null && result.isPresent());
        return result == null ? Optional.empty() : result;
    }

    public boolean isCurrent(AdminPrincipal principal) {
        return store.findById(principal.credentialId())
                .filter(
                        c ->
                                c.eligible()
                                        && c.accountId() == principal.accountId()
                                        && c.securityVersion() == principal.securityVersion())
                .isPresent();
    }

    public Optional<String> changePassword(
            AdminPrincipal principal,
            long expectedVersion,
            String current,
            String next,
            String confirmation) {
        var invalid = PasswordRules.validateNew(next, confirmation);
        if (invalid.isPresent()) return invalid;
        if (current == null || current.getBytes(StandardCharsets.UTF_8).length > 72)
            return Optional.of("현재 비밀번호를 확인해 주세요.");
        return transactions.execute(
                tx -> {
                    var found = store.lockById(principal.credentialId());
                    if (found.isEmpty()
                            || !found.get().eligible()
                            || found.get().accountId() != principal.accountId()
                            || found.get().securityVersion() != expectedVersion
                            || expectedVersion != principal.securityVersion()) {
                        return Optional.of("인증 정보가 변경되었습니다. 다시 로그인해 주세요.");
                    }
                    var credential = found.get();
                    LocalDateTime now = LocalDateTime.now(clock);
                    if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
                        return Optional.of("비밀번호 확인이 잠시 제한되었습니다. 15분 후 다시 시도해 주세요.");
                    }
                    if (!encoder.matches(current, credential.hash())) {
                        int previous = credential.lockedUntil() != null ? 0 : credential.failures();
                        int count = previous + 1;
                        store.failures(
                                credential.id(),
                                count,
                                count >= 5 ? now.plusMinutes(15) : null,
                                now);
                        return Optional.of("현재 비밀번호를 확인해 주세요.");
                    }
                    if (encoder.matches(next, credential.hash()))
                        return Optional.of("기존과 다른 비밀번호를 입력해 주세요.");
                    store.changePassword(credential.id(), encoder.encode(next), now);
                    store.audit(
                            principal.accountId(),
                            "ADMIN_PASSWORD_CHANGED",
                            "관리자 비밀번호 변경 및 이전 세션 철회",
                            now);
                    return Optional.empty();
                });
    }
}
