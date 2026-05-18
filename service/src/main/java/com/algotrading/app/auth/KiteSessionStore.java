package com.algotrading.app.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory store for the current Kite session.
 *
 * <p>Access tokens expire at 06:00 IST each day (or on logout).
 * This store is intentionally kept simple — it holds exactly one session
 * at a time. For a multi-user setup this would be keyed by userId.</p>
 */
public class KiteSessionStore {

    /**
     * Immutable snapshot of one authenticated session.
     */
    public record Session(
            String accessToken,
            String publicToken,
            String userId,
            Instant createdAt
    ) {}

    private final AtomicReference<Session> current = new AtomicReference<>();

    /** Save a newly obtained session. */
    public void save(Session session) {
        current.set(session);
    }

    /** Return the current session, if one exists. */
    public Optional<Session> current() {
        return Optional.ofNullable(current.get());
    }

    /** Returns {@code true} if a session has been established. */
    public boolean isAuthenticated() {
        return current.get() != null;
    }

    /** Clear the session (e.g. on logout or token expiry detection). */
    public void clear() {
        current.set(null);
    }
}