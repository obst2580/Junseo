package com.junseo.moment;

import com.junseo.common.ApiException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The single place that answers "may this user see that moment" and "who can see it". */
@Component
public class MomentAccess {

    private final MomentRepository moments;

    public MomentAccess(MomentRepository moments) {
        this.moments = moments;
    }

    /** Missing and invisible are indistinguishable on purpose (404 either way). */
    public Moment requireVisible(long viewerId, long momentId) {
        return moments.findVisible(viewerId, momentId).orElseThrow(ApiException::notFound);
    }

    public Set<Long> visibleIds(long viewerId, Collection<Long> momentIds) {
        return momentIds.isEmpty() ? Set.of() : new HashSet<>(moments.findVisibleIds(viewerId, momentIds));
    }

    /** Everyone who can currently see the moment: its sender plus still-friend recipients. */
    public List<Long> audience(Moment moment) {
        List<Long> ids = new ArrayList<>(moments.findCurrentRecipientIds(moment.getId()));
        ids.add(moment.getSenderId());
        return ids;
    }
}
