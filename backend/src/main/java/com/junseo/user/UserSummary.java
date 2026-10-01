package com.junseo.user;

public record UserSummary(long id, String displayName) {

    public static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getDisplayName());
    }
}
