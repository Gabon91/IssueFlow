package com.att.tdp.issueflow.ticket.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditActor;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLog;
import com.att.tdp.issueflow.auditlog.AuditLogRepository;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Pins down the escalation rules from {@code ARCHITECTURE.md} §I6:
 * (a) one-step bump LOW→MEDIUM→HIGH→CRITICAL,
 * (b) CRITICAL is the ceiling and only gets {@code isOverdue=true},
 * (c) every mutation emits a {@code AUTO_ESCALATE} audit row with {@code actor=SYSTEM},
 * (d) empty candidate set is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock AuditLogRepository auditLogRepository;

    private final Clock fixed = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private EscalationService service() {
        return new EscalationService(ticketRepository, auditLogRepository, fixed, new SimpleMeterRegistry());
    }

    private Ticket ticket(long id, TicketPriority priority) {
        Ticket t = Ticket.builder()
            .title("t" + id).status(com.att.tdp.issueflow.ticket.domain.TicketStatus.TODO)
            .priority(priority).type(com.att.tdp.issueflow.ticket.domain.TicketType.BUG)
            .dueDate(Instant.parse("2026-05-30T00:00:00Z")).isOverdue(false)
            .build();
        t.setId(id);
        return t;
    }

    @Test
    void noCandidatesIsNoOp() {
        when(ticketRepository.findOverdueCandidates(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        int bumped = service().escalate(50);

        assertThat(bumped).isZero();
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void bumpsLowToMediumAndAudits() {
        Ticket t = ticket(7L, TicketPriority.LOW);
        when(ticketRepository.findOverdueCandidates(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(t));

        int bumped = service().escalate(50);

        assertThat(bumped).isOne();
        assertThat(t.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(t.isOverdue()).isTrue();
        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(cap.capture());
        AuditLog row = cap.getValue();
        assertThat(row.getAction()).isEqualTo(AuditAction.AUTO_ESCALATE);
        assertThat(row.getActor()).isEqualTo(AuditActor.SYSTEM);
        assertThat(row.getEntityType()).isEqualTo(AuditEntityType.TICKET);
        assertThat(row.getEntityId()).isEqualTo(7L);
        assertThat(row.getPerformedBy()).isNull();
        assertThat(row.getPayload()).contains("\"priorityBefore\":\"LOW\"")
            .contains("\"priorityAfter\":\"MEDIUM\"");
    }

    @Test
    void criticalIsCeilingButStillFlagged() {
        Ticket t = ticket(9L, TicketPriority.CRITICAL);
        when(ticketRepository.findOverdueCandidates(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(t));

        service().escalate(50);

        assertThat(t.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(t.isOverdue()).isTrue();
        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getPayload())
            .contains("\"priorityBefore\":\"CRITICAL\"")
            .contains("\"priorityAfter\":\"CRITICAL\"");
    }

    @Test
    void bumpsEachCandidateOneStep() {
        Ticket low = ticket(1L, TicketPriority.LOW);
        Ticket med = ticket(2L, TicketPriority.MEDIUM);
        Ticket high = ticket(3L, TicketPriority.HIGH);
        when(ticketRepository.findOverdueCandidates(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(low, med, high));

        int bumped = service().escalate(50);

        assertThat(bumped).isEqualTo(3);
        assertThat(low.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(med.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(high.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        verify(auditLogRepository, times(3)).save(any());
    }
}
