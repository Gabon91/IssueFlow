package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.audit.Audited;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnprocessableEntityException;
import com.att.tdp.issueflow.dependency.dto.DependencyEntry;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the "is blocked by" edges between tickets. Enforces invariant I3 at
 * insertion time by rejecting self-edges, cross-project edges, duplicate edges and edges that
 * would close a cycle. The companion guard against transitioning to {@code DONE} with open
 * blockers lives in {@code TicketService} via {@code TicketRepository.countOpenBlockers}.
 */
@Service
@Transactional
public class DependencyService {

    private final TicketRepository ticketRepository;
    private final TicketDependencyRepository dependencyRepository;

    public DependencyService(TicketRepository ticketRepository,
                             TicketDependencyRepository dependencyRepository) {
        this.ticketRepository = ticketRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Transactional(readOnly = true)
    public List<DependencyEntry> findBlockers(Long ticketId) {
        requireTicket(ticketId);
        return dependencyRepository.findById_TicketId(ticketId).stream()
            .map(d -> {
                Ticket b = d.getBlockedBy();
                return new DependencyEntry(b.getId(), b.getTitle(), b.getStatus());
            })
            .toList();
    }

    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.DEPENDENCY, idArg = 0)
    public void add(Long ticketId, Long blockedById) {
        if (Objects.equals(ticketId, blockedById)) {
            throw new UnprocessableEntityException("A ticket cannot depend on itself");
        }
        Ticket ticket = requireTicket(ticketId);
        Ticket blocker = ticketRepository.findById(blockedById)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", blockedById));
        if (!Objects.equals(ticket.getProject().getId(), blocker.getProject().getId())) {
            throw new UnprocessableEntityException(
                "Tickets " + ticketId + " and " + blockedById + " are not in the same project");
        }
        if (dependencyRepository.existsById_TicketIdAndId_BlockedByTicketId(ticketId, blockedById)) {
            throw new UnprocessableEntityException(
                "Ticket " + ticketId + " is already blocked by " + blockedById);
        }
        if (wouldCreateCycle(ticketId, blockedById)) {
            throw new UnprocessableEntityException(
                "Adding this dependency would create a cycle");
        }
        TicketDependencyId pk = new TicketDependencyId(ticketId, blockedById);
        TicketDependency edge = TicketDependency.builder()
            .id(pk)
            .ticket(ticket)
            .blockedBy(blocker)
            .build();
        dependencyRepository.save(edge);
    }

    @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.DEPENDENCY, idArg = 0)
    public void remove(Long ticketId, Long blockedById) {
        TicketDependencyId pk = new TicketDependencyId(ticketId, blockedById);
        if (!dependencyRepository.existsById(pk)) {
            throw new ResourceNotFoundException(
                "Dependency (ticket=" + ticketId + ", blockedBy=" + blockedById + ") not found");
        }
        dependencyRepository.deleteById(pk);
    }

    /**
     * Returns {@code true} if the prospective edge {@code ticketId → blockedById} would close
     * a cycle. We walk the existing "is blocked by" graph starting at {@code blockedById}; if
     * {@code ticketId} is reachable as a blocker (directly or transitively) then the new edge
     * would form a loop and must be rejected.
     */
    private boolean wouldCreateCycle(Long ticketId, Long blockedById) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(blockedById);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!seen.add(current)) continue;
            if (Objects.equals(current, ticketId)) return true;
            stack.addAll(dependencyRepository.findBlockerIds(current));
        }
        return false;
    }

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", ticketId));
    }
}
