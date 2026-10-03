package com.junseo.safety;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reporterId;
    private Long targetUserId;
    /** moment | comment | message | group-message | user */
    private String kind;
    private Long targetId;
    /** spam | abuse | sexual | violence | other */
    private String reason;
    private String detail;
    /** The reported text as it was (comments, messages), so the operator can judge it even after it is deleted. */
    private String snapshot;
    private Instant createdAt;
    private Instant resolvedAt;
    private String resolution;

    protected Report() {}

    public Report(long reporterId, long targetUserId, String kind, Long targetId, String reason, String detail, String snapshot, Instant createdAt) {
        this.reporterId = reporterId;
        this.targetUserId = targetUserId;
        this.kind = kind;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.snapshot = snapshot;
        this.createdAt = createdAt;
    }

    void resolve(String resolution, Instant now) {
        this.resolution = resolution;
        this.resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getKind() {
        return kind;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolution() {
        return resolution;
    }
}
