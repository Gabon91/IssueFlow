package com.att.tdp.issueflow.auditlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Immutable record of a domain mutation. Rows are append-only and written in the same
 * transaction as the action they describe (via {@code AuditAspect}). Background jobs
 * write rows with {@link #actor} set to {@link AuditActor#SYSTEM}.
 *
 * <p>The {@link #payload} column holds a JSON string (before/after snapshot or diff)
 * serialized by the service layer.</p>
 */
@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "ix_audit_timestamp", columnList = "timestamp"),
        @Index(name = "ix_audit_performed_by", columnList = "performed_by")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private AuditEntityType entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Null for {@link AuditActor#SYSTEM} events. */
    @Column(name = "performed_by")
    private Long performedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditActor actor;

    @CreatedDate
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    /** Free-form JSON payload (before/after snapshot or diff). */
    @Column(columnDefinition = "text")
    private String payload;
}
