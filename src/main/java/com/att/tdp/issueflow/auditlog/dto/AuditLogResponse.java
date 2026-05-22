package com.att.tdp.issueflow.auditlog.dto;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditActor;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Wire shape returned by {@code GET /audit-logs}. Mirrors {@code AuditLogResponse} in {@code openapi.yaml}. */
public record AuditLogResponse(
    Long id,
    AuditAction action,
    AuditEntityType entityType,
    Long entityId,
    Long performedBy,
    AuditActor actor,
    Instant timestamp,
    JsonNode payload
) {}
