package com.att.tdp.issueflow.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link User}. Lookup by username/email backs the authentication
 * flow; the workload queries back the auto-assignment policy and the {@code /workload}
 * reporting endpoint described in {@code ARCHITECTURE.md} §3.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    /**
     * Returns one row per {@link Role#DEVELOPER} with their current count of non-{@code DONE}
     * tickets. Drives the workload reporting endpoint.
     */
    @Query("""
        SELECT u.id AS userId,
               u.username AS username,
               u.fullName AS fullName,
               (SELECT COUNT(t) FROM Ticket t
                 WHERE t.assignee = u
                   AND t.status <> com.att.tdp.issueflow.ticket.domain.TicketStatus.DONE) AS openTicketCount
          FROM User u
         WHERE u.role = com.att.tdp.issueflow.user.Role.DEVELOPER
         ORDER BY openTicketCount ASC, u.id ASC
        """)
    List<UserWorkload> findDeveloperWorkloads();

    /**
     * Returns the developer(s) with the fewest open tickets, ordered ascending. Pass
     * {@code Pageable.ofSize(1)} to fetch a single least-loaded developer for auto-assignment.
     * Ties are broken by {@code id} so the result is deterministic.
     */
    @Query("""
        SELECT u FROM User u
         WHERE u.role = com.att.tdp.issueflow.user.Role.DEVELOPER
         ORDER BY (SELECT COUNT(t) FROM Ticket t
                    WHERE t.assignee = u
                      AND t.status <> com.att.tdp.issueflow.ticket.domain.TicketStatus.DONE) ASC,
                  u.id ASC
        """)
    List<User> findDevelopersOrderedByOpenLoad(Pageable pageable);
}
