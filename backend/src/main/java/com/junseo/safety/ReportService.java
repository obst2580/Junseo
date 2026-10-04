package com.junseo.safety;

import com.junseo.chat.Message;
import com.junseo.chat.MessageRepository;
import com.junseo.comment.Comment;
import com.junseo.comment.CommentRepository;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import com.junseo.common.Texts;
import com.junseo.group.GroupMemberRepository;
import com.junseo.group.GroupMessage;
import com.junseo.group.GroupMessageRepository;
import com.junseo.mail.Mailer;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.media.MediaUrlSigner;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentAccess;
import com.junseo.moment.MomentRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고: 볼 수 있는 것만 신고할 수 있다 (내 사진함의 사진, 그 사진의 댓글, 내 대화의 메시지, 내 단챗의 메시지, 사람).
 * 들어오면 로그에 남기고 운영자 메일(JUNSEO_ADMIN_EMAIL)로 알린다. 운영자는 /api/admin/reports 로 보고 처리한다.
 */
@Service
public class ReportService {

    /** 처리한 신고를 보관하는 기간 (개인정보처리방침과 같게). 새 신고가 들어올 때 지난 것을 지운다. */
    static final Duration KEEP_RESOLVED = Duration.ofDays(365);
    public static final Set<String> KINDS = Set.of("moment", "comment", "message", "group-message", "user");
    public static final Set<String> REASONS = Set.of("spam", "abuse", "sexual", "violence", "other");
    private static final Map<String, String> REASON_NAMES = Map.of(
            "spam", "스팸 · 광고", "abuse", "괴롭힘 · 욕설", "sexual", "성적인 내용", "violence", "폭력 · 위험", "other", "기타");

    public record ReportRequest(String kind, Long targetId, Long userId, String reason, String detail) {}

    /** What the operator sees. imageUrl: the reported photo while it still exists (signed, 7 days). */
    public record ReportView(
            long id,
            String kind,
            Long targetId,
            String reason,
            String detail,
            String snapshot,
            String imageUrl,
            UserSummary reporter,
            UserSummary target,
            Instant createdAt,
            Instant resolvedAt,
            String resolution) {}

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reports;
    private final MomentAccess momentAccess;
    private final MomentRepository moments;
    private final CommentRepository comments;
    private final MessageRepository messages;
    private final GroupMessageRepository groupMessages;
    private final GroupMemberRepository groupMembers;
    private final UserRepository users;
    private final MediaUrlSigner signer;
    private final Mailer mailer;
    private final JunseoProperties props;
    private final Clock clock;

    public ReportService(
            ReportRepository reports,
            MomentAccess momentAccess,
            MomentRepository moments,
            CommentRepository comments,
            MessageRepository messages,
            GroupMessageRepository groupMessages,
            GroupMemberRepository groupMembers,
            UserRepository users,
            MediaUrlSigner signer,
            Mailer mailer,
            JunseoProperties props,
            Clock clock) {
        this.reports = reports;
        this.momentAccess = momentAccess;
        this.moments = moments;
        this.comments = comments;
        this.messages = messages;
        this.groupMessages = groupMessages;
        this.groupMembers = groupMembers;
        this.users = users;
        this.signer = signer;
        this.mailer = mailer;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public long create(long reporter, ReportRequest r) {
        if (r == null || r.kind() == null || !KINDS.contains(r.kind())) {
            throw invalid("무엇을 신고하는지 알려 주세요.");
        }
        if (r.reason() == null || !REASONS.contains(r.reason())) {
            throw invalid("신고 이유를 골라 주세요.");
        }
        String detail = r.detail() == null || r.detail().isBlank() ? null : r.detail().strip();
        if (detail != null && detail.length() > 500) {
            throw invalid("자세한 내용은 500자까지 적을 수 있어요.");
        }
        long targetUser;
        String snapshot = null;
        switch (r.kind()) {
            case "moment" -> {
                Moment m = momentAccess.requireVisible(reporter, requireId(r.targetId()));
                targetUser = m.getSenderId();
            }
            case "comment" -> {
                Comment c = comments.findById(requireId(r.targetId())).orElseThrow(ApiException::notFound);
                momentAccess.requireVisible(reporter, c.getMomentId());
                targetUser = c.getAuthorId();
                snapshot = c.getText();
            }
            case "message" -> {
                Message m = messages.findById(requireId(r.targetId())).orElseThrow(ApiException::notFound);
                if (m.getReceiverId() != reporter && m.getSenderId() != reporter) {
                    throw ApiException.notFound();
                }
                targetUser = m.getSenderId();
                snapshot = m.getText();
            }
            case "group-message" -> {
                GroupMessage m = groupMessages.findById(requireId(r.targetId())).orElseThrow(ApiException::notFound);
                if (!groupMembers.existsByGroupIdAndUserId(m.getGroupId(), reporter)) {
                    throw ApiException.notFound();
                }
                targetUser = m.getSenderId();
                snapshot = m.getText();
            }
            default -> targetUser = users.findById(requireId(r.userId())).orElseThrow(ApiException::notFound).getId();
        }
        if (targetUser == reporter) {
            throw invalid("내가 올린 것은 신고할 수 없어요.");
        }
        reports.deleteResolvedBefore(clock.instant().minus(KEEP_RESOLVED));
        Report saved = reports.save(new Report(
                reporter, targetUser, r.kind(), "user".equals(r.kind()) ? null : r.targetId(), r.reason(), detail, snapshot, clock.instant()));
        log.warn("Report #{} kind={} target={} user={} reason={} by={}", saved.getId(), r.kind(), saved.getTargetId(), targetUser, r.reason(), reporter);
        notifyOperator(saved);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<ReportView> list(boolean all) {
        List<Report> rows = all ? reports.findAllByOrderByCreatedAtDesc(Limit.of(200)) : reports.findByResolvedAtIsNullOrderByCreatedAtAsc(Limit.of(200));
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> {
            if (r.getReporterId() != null) {
                ids.add(r.getReporterId());
            }
            if (r.getTargetUserId() != null) {
                ids.add(r.getTargetUserId());
            }
        });
        Map<Long, User> people = users.mapById(ids);
        return rows.stream().map(r -> new ReportView(
                        r.getId(),
                        r.getKind(),
                        r.getTargetId(),
                        r.getReason(),
                        r.getDetail(),
                        r.getSnapshot(),
                        "moment".equals(r.getKind()) && r.getTargetId() != null && moments.existsById(r.getTargetId())
                                ? signer.url(r.getTargetId(), Variant.FULL)
                                : null,
                        summary(people, r.getReporterId()),
                        summary(people, r.getTargetUserId()),
                        r.getCreatedAt(),
                        r.getResolvedAt(),
                        r.getResolution()))
                .toList();
    }

    @Transactional
    public void resolve(long id, String resolution) {
        Report r = reports.findById(id).orElseThrow(ApiException::notFound);
        r.resolve(resolution == null || resolution.isBlank() ? "처리함" : Texts.truncate(resolution.strip(), 200), clock.instant());
    }

    private void notifyOperator(Report r) {
        String to = props.admin().email();
        if (to == null || to.isBlank()) {
            return;
        }
        String text = "새 신고 #" + r.getId() + "\n"
                + "종류: " + r.getKind() + (r.getTargetId() == null ? "" : " #" + r.getTargetId()) + "\n"
                + "이유: " + REASON_NAMES.get(r.getReason()) + "\n"
                + (r.getDetail() == null ? "" : "내용: " + r.getDetail() + "\n")
                + (r.getSnapshot() == null ? "" : "신고된 글: " + r.getSnapshot() + "\n")
                + "\n24시간 안에 확인해 주세요: GET /api/admin/reports";
        mailer.send(to, "[잡다] 새 신고 #" + r.getId(), text);
    }

    private static UserSummary summary(Map<Long, User> people, Long id) {
        User u = id == null ? null : people.get(id);
        return u == null ? null : UserSummary.of(u);
    }

    private static long requireId(Long id) {
        if (id == null) {
            throw invalid("신고할 대상을 알려 주세요.");
        }
        return id;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
