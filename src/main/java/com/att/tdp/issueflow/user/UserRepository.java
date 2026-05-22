package com.att.tdp.issueflow.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Count of live (non-soft-deleted) users with the given role; backs the last-ADMIN guard. */
    long countByRole(Role role);

    // ----- Soft-delete escape hatches (ADMIN-only) -----

    @Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL", nativeQuery = true)
    List<User> findDeleted();

    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingDeleted(@Param("id") Long id);

    /** Uniqueness check that includes soft-deleted rows; backs nicer 409 on registration. */
    @Query(value = "SELECT count(*) > 0 FROM users WHERE username = :username", nativeQuery = true)
    boolean existsByUsernameIncludingDeleted(@Param("username") String username);

    @Query(value = "SELECT count(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean existsByEmailIncludingDeleted(@Param("email") String email);

    @Modifying
    @Query(value = "UPDATE users SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
    int restore(@Param("id") Long id);

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
     * Per-project variant of {@link #findDeveloperWorkloads()}: counts only tickets that live
     * in {@code projectId}. Drives {@code GET /projects/{projectId}/workload}. All developers
     * are returned, including those with zero open tickets in the project, so the response is
     * also usable as an "available candidates" list.
     */
    @Query("""
        SELECT u.id AS userId,
               u.username AS username,
               u.fullName AS fullName,
               (SELECT COUNT(t) FROM Ticket t
                 WHERE t.assignee = u
                   AND t.project.id = :projectId
                   AND t.status <> com.att.tdp.issueflow.ticket.domain.TicketStatus.DONE) AS openTicketCount
          FROM User u
         WHERE u.role = com.att.tdp.issueflow.user.Role.DEVELOPER
         ORDER BY openTicketCount ASC, u.id ASC
        """)
    List<UserWorkload> findDeveloperWorkloadsByProject(@Param("projectId") Long projectId);

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
