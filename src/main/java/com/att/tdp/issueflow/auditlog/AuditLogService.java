package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side façade over {@link AuditLogRepository}. Builds a {@link Specification} from
 * the optional filters exposed by {@code GET /audit-logs} and returns a paginated list of
 * {@link AuditLogResponse}, newest first. Append-only writes happen via the AOP aspect.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    /** Default page size when {@code size} is omitted; mirrors a sensible upper bound for UIs. */
    static final int DEFAULT_PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<AuditLogResponse> search(AuditEntityType entityType, Long entityId,
                                         AuditAction action, AuditActor actor,
                                         Long performedBy, Instant from, Instant to,
                                         Integer page, Integer size) {
        Pageable pageable = PageRequest.of(
            page == null || page < 0 ? 0 : page,
            size == null || size < 1 ? DEFAULT_PAGE_SIZE : size,
            Sort.by(Sort.Direction.DESC, "timestamp"));
        Specification<AuditLog> spec = filter(entityType, entityId, action, actor, performedBy, from, to);
        return auditLogRepository.findAll(spec, pageable).getContent().stream()
            .map(this::toResponse).toList();
    }

    private AuditLogResponse toResponse(AuditLog a) {
        JsonNode payload = null;
        if (a.getPayload() != null && !a.getPayload().isBlank()) {
            try {
                payload = objectMapper.readTree(a.getPayload());
            } catch (JsonProcessingException ex) {
                payload = null;
            }
        }
        return new AuditLogResponse(
            a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(),
            a.getPerformedBy(), a.getActor(), a.getTimestamp(), payload);
    }

    static Specification<AuditLog> filter(AuditEntityType entityType, Long entityId,
                                          AuditAction action, AuditActor actor,
                                          Long performedBy, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (entityType != null) preds.add(cb.equal(root.get("entityType"), entityType));
            if (entityId   != null) preds.add(cb.equal(root.get("entityId"),   entityId));
            if (action     != null) preds.add(cb.equal(root.get("action"),     action));
            if (actor      != null) preds.add(cb.equal(root.get("actor"),      actor));
            if (performedBy != null) preds.add(cb.equal(root.get("performedBy"), performedBy));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to   != null) preds.add(cb.lessThanOrEqualTo(root.get("timestamp"),   to));
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
