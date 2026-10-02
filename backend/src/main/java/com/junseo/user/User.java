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
    /** Carried in every login token ({@code ver}); bumping it ends all earlier logins. */
    private int tokenVersion;

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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** New password; every login issued before (other phones included) stops working. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion += 1;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

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
