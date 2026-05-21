package com.att.tdp.issueflow.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link Comment}. Comments are listed per-ticket in
 * chronological order; the {@code Pageable} variant supports large discussion threads.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId, Pageable pageable);

    long countByTicketId(Long ticketId);

    long countByAuthorId(Long authorId);
}
