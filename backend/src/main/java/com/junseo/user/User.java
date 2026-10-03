package com.junseo.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String passwordHash;
    private String displayName;
    private String inviteCode;
    private Instant createdAt;
    private String externalIssuer;
    private String externalSubject;
    private boolean onboarded = true;

    protected User() {}

    public User(String email, String passwordHash, String displayName, String inviteCode, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public static User platform(String issuer, String subject, String email, String inviteCode, Instant createdAt) {
        User user = new User(email, null, "친구", inviteCode, createdAt);
        user.externalIssuer = issuer;
        user.externalSubject = subject;
        user.onboarded = false;
        return user;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.onboarded = true;
    }

    public boolean isOnboarded() { return onboarded; }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
