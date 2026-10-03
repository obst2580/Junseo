package com.junseo.user;

public record Me(long id, String email, String displayName, String inviteCode, long friendCount, int friendLimit, boolean needsOnboarding) {}
