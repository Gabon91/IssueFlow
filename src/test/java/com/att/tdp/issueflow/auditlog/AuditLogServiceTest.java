package com.att.tdp.issueflow.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Pins down the audit log read-side: filter spec composition, paging defaults, ordering by
 * timestamp DESC and JSON payload parsing. We treat the repository as a black box and verify
 * the {@link Pageable} we pass to it.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks AuditLogService service;

    private AuditLog row(long id, AuditEntityType type, long entityId, AuditAction action,
                        AuditActor actor, Long by, Instant ts, String payload) {
        return AuditLog.builder()
            .id(id).entityType(type).entityId(entityId).action(action)
            .actor(actor).performedBy(by).timestamp(ts).payload(payload).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsToFirstPageNewestFirst() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(auditLogRepository).findAll(any(Specification.class), cap.capture());
        Pageable p = cap.getValue();
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(AuditLogService.DEFAULT_PAGE_SIZE);
        assertThat(p.getSort().getOrderFor("timestamp").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @SuppressWarnings("unchecked")
    void honorsExplicitPageAndSize() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, null, null, null, 3, 7);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(auditLogRepository).findAll(any(Specification.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(PageRequest.of(3, 7, Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsEntityToResponseIncludingParsedPayload() {
        Instant ts = Instant.parse("2026-01-02T03:04:05Z");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                row(1L, AuditEntityType.TICKET, 99L, AuditAction.UPDATE,
                    AuditActor.USER, 8L, ts, "{\"method\":\"update\"}"))));

        List<AuditLogResponse> out = service.search(
            AuditEntityType.TICKET, 99L, null, null, null, null, null, null, null);

        assertThat(out).hasSize(1);
        AuditLogResponse r = out.get(0);
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.entityType()).isEqualTo(AuditEntityType.TICKET);
        assertThat(r.entityId()).isEqualTo(99L);
        assertThat(r.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(r.actor()).isEqualTo(AuditActor.USER);
        assertThat(r.performedBy()).isEqualTo(8L);
        assertThat(r.timestamp()).isEqualTo(ts);
        assertThat(r.payload().get("method").asText()).isEqualTo("update");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unparseablePayloadFallsBackToNullWithoutThrowing() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                row(1L, AuditEntityType.TICKET, 1L, AuditAction.CREATE,
                    AuditActor.SYSTEM, null, Instant.now(), "not-json"))));

        List<AuditLogResponse> out = service.search(null, null, null, null, null, null, null, null, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).payload()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyAndNullPayloadStaysNull() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                row(1L, AuditEntityType.TICKET, 1L, AuditAction.CREATE,
                    AuditActor.SYSTEM, null, Instant.now(), ""),
                row(2L, AuditEntityType.TICKET, 1L, AuditAction.DELETE,
                    AuditActor.SYSTEM, null, Instant.now(), null))));

        List<AuditLogResponse> out = service.search(null, null, null, null, null, null, null, null, null);

        assertThat(out).extracting(AuditLogResponse::payload).containsExactly(null, null);
    }
}
