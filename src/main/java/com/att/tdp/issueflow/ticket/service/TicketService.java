package com.att.tdp.issueflow.ticket.service;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.audit.Audited;
import com.att.tdp.issueflow.common.error.BlockedByDependencyException;
import com.att.tdp.issueflow.common.error.IllegalStateTransitionException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.api.TicketMapper;
import com.att.tdp.issueflow.ticket.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.ticket.api.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStateMachine;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the Ticket aggregate. Enforces invariants I1 (forward-only status),
 * I2 (DONE immutability), I3 (blocker guard before DONE), I4 (soft-delete) and I7
 * (auto-assignment to the least-loaded developer when {@code assigneeId} is omitted).
 * Optimistic locking (I5) is supplied by JPA via {@link Ticket#getVersion()}.
 */
@Service
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketMapper ticketMapper;
    private final Counter ticketsCreated;
    private final Counter ticketsAutoAssigned;

    public TicketService(TicketRepository ticketRepository,
                         ProjectRepository projectRepository,
                         UserRepository userRepository,
                         TicketMapper ticketMapper,
                         MeterRegistry meterRegistry) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.ticketMapper = ticketMapper;
        this.ticketsCreated = Counter.builder("issueflow.tickets.created")
            .description("Tickets created via POST /tickets")
            .register(meterRegistry);
        this.ticketsAutoAssigned = Counter.builder("issueflow.tickets.auto_assigned")
            .description("Tickets created without an explicit assignee that received one via I7 auto-assignment")
            .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findByProject(Long projectId) {
        requireProject(projectId);
        return ticketRepository.findByProjectId(projectId, Pageable.unpaged())
            .map(ticketMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id) {
        return ticketMapper.toResponse(loadDetail(id));
    }

    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.TICKET)
    @Observed(name = "issueflow.ticket.create", contextualName = "ticket.create")
    public TicketResponse create(TicketCreateRequest req) {
        Project project = requireProject(req.projectId());
        User assignee = resolveAssignee(req.assigneeId(), project.getId());
        Ticket ticket = Ticket.builder()
            .title(req.title())
            .description(req.description())
            .status(req.status())
            .priority(req.priority())
            .type(req.type())
            .project(project)
            .assignee(assignee)
            .dueDate(req.dueDate())
            .isOverdue(false)
            .build();
        Ticket saved = ticketRepository.save(ticket);
        ticketsCreated.increment();
        if (req.assigneeId() == null && assignee != null) {
            ticketsAutoAssigned.increment();
        }
        return ticketMapper.toResponse(saved);
    }

    @Audited(action = AuditAction.UPDATE, entityType = AuditEntityType.TICKET, idArg = 0)
    @Observed(name = "issueflow.ticket.update", contextualName = "ticket.update")
    public TicketResponse update(Long id, TicketUpdateRequest req) {
        Ticket t = load(id);
        if (t.getStatus() == TicketStatus.DONE) {
            throw new IllegalStateTransitionException("Ticket " + id + " is DONE and immutable");
        }
        if (req.status() != null && req.status() != t.getStatus()) {
            TicketStateMachine.assertTransition(t.getStatus(), req.status());
            if (req.status() == TicketStatus.DONE
                && ticketRepository.countOpenBlockers(id) > 0) {
                throw new BlockedByDependencyException(
                    "Ticket " + id + " has open blockers and cannot move to DONE");
            }
            t.setStatus(req.status());
        }
        if (req.title() != null) t.setTitle(req.title());
        if (req.description() != null) t.setDescription(req.description());
        if (req.priority() != null && req.priority() != t.getPriority()) {
            t.setPriority(req.priority());
            t.setOverdue(false);
        }
        if (req.assigneeId() != null) {
            t.setAssignee(userRepository.findById(req.assigneeId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", req.assigneeId())));
        }
        if (req.dueDate() != null) t.setDueDate(req.dueDate());
        return ticketMapper.toResponse(t);
    }

    @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.TICKET, idArg = 0)
    public void softDelete(Long id) {
        Ticket t = load(id);
        t.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findDeleted(Long projectId) {
        return ticketRepository.findDeleted(Pageable.unpaged()).stream()
            .filter(t -> Objects.equals(t.getProject().getId(), projectId))
            .map(ticketMapper::toResponse).toList();
    }

    @Audited(action = AuditAction.RESTORE, entityType = AuditEntityType.TICKET, idArg = 0)
    public void restore(Long id) {
        Ticket t = ticketRepository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));
        ticketRepository.restore(t.getId());
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private Ticket load(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));
    }

    private Ticket loadDetail(Long id) {
        return ticketRepository.findWithGraphById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));
    }

    /** I7: explicit assignee, else least-loaded developer, else unassigned. */
    private User resolveAssignee(Long explicit, Long projectId) {
        if (explicit != null) {
            return userRepository.findById(explicit)
                .orElseThrow(() -> ResourceNotFoundException.of("User", explicit));
        }
        List<User> candidates = userRepository.findDevelopersOrderedByOpenLoad(PageRequest.of(0, 1));
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}
