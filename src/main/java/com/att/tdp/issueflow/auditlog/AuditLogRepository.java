package com.att.tdp.issueflow.auditlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link AuditLog}. Rows are append-only; the controller layer
 * exposes filter-and-paginate reads only. {@link JpaSpecificationExecutor} backs the
 * multi-criteria query endpoint (filter by entity type/id, actor, performer, time range).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        AuditEntityType entityType, Long entityId, Pageable pageable);

    Page<AuditLog> findByPerformedByOrderByTimestampDesc(Long performedBy, Pageable pageable);

    Page<AuditLog> findByActorOrderByTimestampDesc(AuditActor actor, Pageable pageable);
}
