package com.att.tdp.issueflow.ticket.escalation;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditActor;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLog;
import com.att.tdp.issueflow.auditlog.AuditLogRepository;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent overdue-ticket escalator. Bumps {@link TicketPriority} one level per run for
 * tickets whose {@code dueDate} is past and that are not yet flagged {@code isOverdue}.
 * Per invariant I6 in {@code ARCHITECTURE.md}, escalation never moves beyond
 * {@link TicketPriority#CRITICAL}; once at the ceiling only {@code isOverdue=true} is stamped.
 * Each mutation writes a manual {@link AuditLog} row with {@link AuditActor#SYSTEM}.
 */
@Service
@Transactional
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final TicketRepository ticketRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final Counter ticketsEscalated;

    public EscalationService(TicketRepository ticketRepository,
                             AuditLogRepository auditLogRepository,
                             Clock clock,
                             MeterRegistry meterRegistry) {
        this.ticketRepository = ticketRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
        this.ticketsEscalated = Counter.builder("issueflow.tickets.escalated")
            .description("Tickets bumped or flagged overdue by the scheduled escalator")
            .register(meterRegistry);
    }

    /**
     * Scans up to {@code batchSize} candidates ordered oldest-first and escalates each.
     * Returns the number of rows that were actually mutated (always == candidates loaded,
     * since the candidate query already filters {@code isOverdue=false}).
     */
    @Observed(name = "issueflow.ticket.escalate", contextualName = "ticket.escalate")
    public int escalate(int batchSize) {
        Instant now = Instant.now(clock);
        List<Ticket> candidates = ticketRepository.findOverdueCandidates(now, PageRequest.of(0, batchSize));
        if (candidates.isEmpty()) {
            return 0;
        }
        int bumped = 0;
        for (Ticket t : candidates) {
            TicketPriority before = t.getPriority();
            TicketPriority after = nextPriority(before);
            if (after != before) {
                t.setPriority(after);
            }
            t.setOverdue(true);
            auditLogRepository.save(AuditLog.builder()
                .action(AuditAction.AUTO_ESCALATE)
                .entityType(AuditEntityType.TICKET)
                .entityId(t.getId())
                .performedBy(null)
                .actor(AuditActor.SYSTEM)
                .payload(payload(before, after))
                .build());
            bumped++;
        }
        ticketsEscalated.increment(bumped);
        log.info("EscalationService: escalated {} overdue tickets", bumped);
        return bumped;
    }

    /** I6: never bumps past CRITICAL; CRITICAL tickets only get {@code isOverdue=true}. */
    private static TicketPriority nextPriority(TicketPriority p) {
        return switch (p) {
            case LOW -> TicketPriority.MEDIUM;
            case MEDIUM -> TicketPriority.HIGH;
            case HIGH -> TicketPriority.CRITICAL;
            case CRITICAL -> TicketPriority.CRITICAL;
        };
    }

    private static String payload(TicketPriority before, TicketPriority after) {
        return "{\"priorityBefore\":\"" + before.name()
            + "\",\"priorityAfter\":\"" + after.name()
            + "\",\"reason\":\"overdue\"}";
    }
}
