package com.junseo.safety;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Oldest first: those are closest to the 24 hour promise. */
    List<Report> findByResolvedAtIsNullOrderByCreatedAtAsc(Limit limit);

    List<Report> findAllByOrderByCreatedAtDesc(Limit limit);

    /** 처리한 지 1년이 지난 신고는 지운다 (개인정보처리방침). */
    @Modifying
    @Query("delete from Report r where r.resolvedAt < :before")
    int deleteResolvedBefore(Instant before);
}
