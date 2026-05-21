package com.att.tdp.issueflow.dependency;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link TicketDependency} (the "is blocked by" edge between two
 * tickets). Spring Data resolves the embedded-id properties via the underscore convention:
 * {@code findById_TicketId} navigates {@code TicketDependency.id.ticketId}.
 *
 * <p>Cycle detection itself is handled in the service layer using a graph walk; this repository
 * only exposes the primitives the walk needs.</p>
 */
@Repository
public interface TicketDependencyRepository extends JpaRepository<TicketDependency, TicketDependencyId> {

    /** Edges where this ticket is the dependent (i.e. the blockers of {@code ticketId}). */
    List<TicketDependency> findById_TicketId(Long ticketId);

    /** Edges where this ticket is the blocker (i.e. the dependents of {@code blockedById}). */
    List<TicketDependency> findById_BlockedByTicketId(Long blockedById);

    boolean existsById_TicketIdAndId_BlockedByTicketId(Long ticketId, Long blockedById);

    long countById_TicketId(Long ticketId);

    /**
     * IDs of all blockers reachable in one hop from {@code ticketId}. Cheaper than fetching
     * the full edge rows when the service is only doing a graph walk for cycle detection.
     */
    @Query("SELECT d.id.blockedByTicketId FROM TicketDependency d WHERE d.id.ticketId = :ticketId")
    List<Long> findBlockerIds(@Param("ticketId") Long ticketId);
}
