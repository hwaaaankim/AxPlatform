package com.axplatform.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Process-local IP protection; credential failures are also persisted in MariaDB. */
public final class LoginAttemptLimiter {
    private record Window(long startedAt, int attempts) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) { this.clock = clock; }

    public synchronized boolean allow(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= 60_000L);
        Window current = windows.get(address);
        if (current == null && windows.size() >= 10_000) return false;
        if (current != null && current.attempts() >= 30) return false;
        windows.put(address, current == null ? new Window(now, 1)
                : new Window(current.startedAt(), current.attempts() + 1));
        return true;
    }
}
