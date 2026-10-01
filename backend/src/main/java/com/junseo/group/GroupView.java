package com.junseo.group;

import com.junseo.user.UserSummary;
import java.time.Instant;
import java.util.List;

/** A group chat; members includes the viewer. A null name means the app shows the other members' names. */
public record GroupView(long id, String name, List<UserSummary> members, Instant createdAt) {}
