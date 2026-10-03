package com.junseo.safety;

import com.junseo.common.security.CurrentUser;
import com.junseo.safety.ReportService.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    public record Created(long id) {}

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** {@code {kind: moment|comment|message|group-message|user, targetId?, userId?, reason, detail?}} */
    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    Created create(@CurrentUser long me, @RequestBody ReportRequest request) {
        return new Created(reports.create(me, request));
    }
}
