package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only audit log endpoint, ADMIN-only. Filters are all optional and AND-ed together;
 * results are returned newest first. Pagination is offered via {@code page}/{@code size} so
 * the audit trail of a busy entity doesn't have to round-trip in one payload.
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> list(
        @RequestParam(required = false) AuditEntityType entityType,
        @RequestParam(required = false) Long entityId,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) AuditActor actor,
        @RequestParam(required = false) Long performedBy,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size) {
        return auditLogService.search(entityType, entityId, action, actor, performedBy, from, to, page, size);
    }
}
