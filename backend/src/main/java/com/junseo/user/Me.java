package com.junseo.user;

/** loginMethod: "platform" (LiliPlanet login) or "password" (local email login, development). */
public record Me(
        long id, String email, String displayName, String inviteCode, long friendCount, int friendLimit, boolean needsOnboarding,
        String loginMethod) {}
