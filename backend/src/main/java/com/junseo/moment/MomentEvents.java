package com.junseo.moment;

import java.util.List;

public final class MomentEvents {

    private MomentEvents() {}

    public record MomentCreated(long momentId, long senderId) {}

    /** The audience is captured before the rows (and the recipient snapshot) disappear. */
    public record MomentDeleted(long momentId, long senderId, List<Long> audience) {}
}
