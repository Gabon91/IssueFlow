package com.att.tdp.issueflow.ticket.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link Ticket}. JPQL/derived queries respect the
 * {@code @SQLRestriction("deleted_at IS NULL")} filter on the entity. The
 * {@link JpaSpecificationExecutor} mix-in backs the multi-criteria list endpoint
 * (filter by project, status, assignee, priority, type, due-date range).
 *
 * <p>Detail-view reads use the {@code "ticket.detail"} entity graph to fetch the project
 * and assignee in one round-trip, eliminating the lazy {@code N+1} that would otherwise
 * appear when serialising a {@code TicketResponse} outside the persistence context.</p>
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = {"project", "assignee"})
    Optional<Ticket> findWithGraphById(Long id);

    Page<Ticket> findByProjectId(Long projectId, Pageable pageable);

    Page<Ticket> findByAssigneeId(Long assigneeId, Pageable pageable);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    /** Workload count for a given assignee, excluding tickets already {@code DONE}. */
    long countByAssigneeIdAndStatusNot(Long assigneeId, TicketStatus status);

    /**
     * Overdue scanner used by the {@code @Scheduled} escalation job: rows whose
     * {@code due_date} has passed and are not yet {@code DONE} nor already flagged.
     * Returned in due-date order so the job processes the oldest first.
     */
    @Query("""
        SELECT t FROM Ticket t
         WHERE t.dueDate IS NOT NULL
           AND t.dueDate < :now
           AND t.status <> com.att.tdp.issueflow.ticket.domain.TicketStatus.DONE
           AND t.isOverdue = false
         ORDER BY t.dueDate ASC
        """)
    List<Ticket> findOverdueCandidates(@Param("now") Instant now, Pageable pageable);

    /**
     * Used by the state-machine guard before a transition to {@code DONE}: counts
     * blockers of {@code ticketId} that are not yet {@code DONE}. A non-zero result
     * means the transition must be rejected.
     */
    @Query("""
        SELECT COUNT(d) FROM TicketDependency d
         WHERE d.ticket.id = :ticketId
           AND d.blockedBy.status <> com.att.tdp.issueflow.ticket.domain.TicketStatus.DONE
        """)
    long countOpenBlockers(@Param("ticketId") Long ticketId);

    /** Pessimistic lock for the rare case where the auto-assignment job needs a single-writer view. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    // ----- Soft-delete escape hatches (ADMIN-only) -----

    @Query(value = "SELECT * FROM tickets WHERE deleted_at IS NOT NULL", nativeQuery = true)
    Page<Ticket> findDeleted(Pageable pageable);

    @Query(value = "SELECT * FROM tickets WHERE id = :id", nativeQuery = true)
    Optional<Ticket> findByIdIncludingDeleted(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE tickets SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
    int restore(@Param("id") Long id);
}
