package com.junseo.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The code mailed for 「비밀번호 찾기」; only its hash is kept. One per person. */
@Entity
@Table(name = "password_resets")
public class PasswordReset {

    @Id
    private Long userId;

    private String codeHash;
    private Instant expiresAt;
    private int attempts;
    private Instant sentAt;

    protected PasswordReset() {}

    public PasswordReset(long userId) {
        this.userId = userId;
    }

    void issue(String codeHash, Instant now, Instant expiresAt) {
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    int failedAttempt() {
        return ++attempts;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
