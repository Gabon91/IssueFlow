package com.att.tdp.issueflow.attachment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link Attachment}. The {@code data} column is lazy by default
 * ({@code @Basic(fetch = LAZY)}); list operations therefore stream only the metadata. Use
 * {@link #findWithDataById} when serving the binary download endpoint.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketIdOrderByUploadedAtAsc(Long ticketId);

    long countByTicketId(Long ticketId);

    /**
     * Sum of bytes already stored against a ticket. Used by the upload guard that enforces
     * the per-ticket attachment budget on top of the per-file 10 MB limit.
     * Returns {@code 0} when the ticket has no attachments.
     */
    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Attachment a WHERE a.ticket.id = :ticketId")
    long sumSizeBytesByTicketId(@Param("ticketId") Long ticketId);

    /** Eagerly fetches the {@code data} blob; reserved for the download endpoint. */
    @EntityGraph(attributePaths = {"data"})
    Optional<Attachment> findWithDataById(Long id);
}
