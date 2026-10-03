package com.junseo.device;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    public static final String KIND_APP = "app";
    public static final String KIND_WIDGET = "widget";
    public static final String ENV_PRODUCTION = "production";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;
    private Long userId;
    private String kind;
    private String environment;
    private String platform;
    private Instant createdAt;
    private Instant updatedAt;

    protected DeviceToken() {}

    public DeviceToken(String token, long userId, String kind, String environment, Instant at) {
        this(token, userId, kind, environment, "ios", at);
    }

    public DeviceToken(String token, long userId, String kind, String environment, String platform, Instant at) {
        this.token = token;
        this.userId = userId;
        this.kind = kind;
        this.environment = environment;
        this.platform = platform;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getPlatform() { return platform; }
}
