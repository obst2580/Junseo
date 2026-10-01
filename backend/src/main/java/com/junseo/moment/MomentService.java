package com.junseo.moment;

import com.junseo.common.ApiException;
import com.junseo.common.CursorPage;
import com.junseo.common.ErrorCode;
import com.junseo.common.Transactions;
import com.junseo.media.ImageProcessor.ProcessedImage;
import com.junseo.media.MediaStorage;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.moment.MomentEvents.MomentCreated;
import com.junseo.moment.MomentEvents.MomentDeleted;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MomentService {

    private final MomentRepository moments;
    private final MomentAccess access;
    private final MomentViews views;
    private final MediaStorage storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MomentService(
            MomentRepository moments,
            MomentAccess access,
            MomentViews views,
            MediaStorage storage,
            ApplicationEventPublisher events,
            Clock clock) {
        this.moments = moments;
        this.access = access;
        this.views = views;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The image is processed before this call so no DB connection is held during decoding.
     * recipientIds null means every current friend; otherwise only those, and each must be a friend.
     */
    @Transactional
    public MomentView create(long senderId, ProcessedImage image, List<Long> recipientIds) {
        Moment moment = moments.save(new Moment(senderId, clock.instant()));
        long id = moment.getId();
        if (recipientIds == null) {
            moments.snapshotRecipients(id, senderId);
        } else {
            Set<Long> chosen = new LinkedHashSet<>(recipientIds);
            chosen.removeIf(Objects::isNull);
            if (chosen.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "받을 친구를 한 명 이상 골라 주세요.");
            }
            // Only rows for actual friends are inserted, so a shorter count means someone isn't a friend.
            if (moments.snapshotChosenRecipients(id, senderId, chosen) != chosen.size()) {
                throw new ApiException(ErrorCode.NOT_FRIENDS, "친구에게만 사진을 보낼 수 있어요.");
            }
        }
        Transactions.afterRollback(() -> deleteFiles(id));
        storage.put(MediaStorage.key(id, Variant.FULL), image.full());
        storage.put(MediaStorage.key(id, Variant.THUMB), image.thumb());
        events.publishEvent(new MomentCreated(id, senderId));
        return views.one(senderId, moment);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentView> list(long viewerId, Long senderId, String cursor, int limit) {
        int size = CursorPage.clampLimit(limit);
        List<Moment> rows = moments.findFeed(viewerId, senderId == null ? 0 : senderId, CursorPage.before(cursor), size + 1);
        return CursorPage.of(rows, size, Moment::getId, page -> views.list(viewerId, page));
    }

    @Transactional(readOnly = true)
    public MomentView detail(long viewerId, long momentId) {
        return views.detail(viewerId, access.requireVisible(viewerId, momentId));
    }

    @Transactional
    public void delete(long userId, long momentId) {
        Moment moment = access.requireVisible(userId, momentId);
        if (moment.getSenderId() != userId) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내가 보낸 사진만 지울 수 있어요.");
        }
        List<Long> audience = access.audience(moment);
        moments.delete(moment);
        Transactions.afterCommit(() -> deleteFiles(momentId));
        events.publishEvent(new MomentDeleted(momentId, userId, audience));
    }

    private void deleteFiles(long momentId) {
        storage.delete(MediaStorage.key(momentId, Variant.FULL));
        storage.delete(MediaStorage.key(momentId, Variant.THUMB));
    }
}
