package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.audit.Audited;
import com.att.tdp.issueflow.common.error.PayloadTooLargeException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnsupportedMimeTypeException;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic for ticket attachments. Enforces the 10 MB per-file cap and the MIME
 * allow-list documented in the OpenAPI contract, and gates deletion to the original
 * uploader or any {@link Role#ADMIN}.
 */
@Service
@Transactional
public class AttachmentService {

    /** Per-file size cap mirrored from {@code openapi.yaml} and {@code spring.servlet.multipart.max-file-size}. */
    static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;

    /** MIME types permitted by the OpenAPI contract. */
    static final Set<String> ALLOWED_MIME = Set.of(
        "image/png", "image/jpeg", "application/pdf", "text/plain");

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             TicketRepository ticketRepository,
                             UserRepository userRepository) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.ATTACHMENT)
    public AttachmentResponse upload(Long ticketId, MultipartFile file, IssueFlowUserDetails currentUser) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", ticketId));
        validate(file);
        User uploader = currentUser == null ? null
            : userRepository.findById(currentUser.getUserId()).orElse(null);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded file", ex);
        }
        Attachment saved = attachmentRepository.save(Attachment.builder()
            .ticket(ticket)
            .uploadedBy(uploader)
            .filename(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename())
            .contentType(file.getContentType())
            .sizeBytes(file.getSize())
            .data(bytes)
            .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listByTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw ResourceNotFoundException.of("Ticket", ticketId);
        }
        return attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId).stream()
            .map(AttachmentService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Attachment download(Long ticketId, Long attachmentId) {
        Attachment a = attachmentRepository.findWithDataById(attachmentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));
        if (!a.getTicket().getId().equals(ticketId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }
        return a;
    }

    @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.ATTACHMENT, idArg = 1)
    public void delete(Long ticketId, Long attachmentId, IssueFlowUserDetails currentUser) {
        Attachment a = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));
        if (!a.getTicket().getId().equals(ticketId)) {
            throw ResourceNotFoundException.of("Attachment", attachmentId);
        }
        if (!canDelete(a, currentUser)) {
            throw new AccessDeniedException("Only the uploader or an ADMIN may delete this attachment");
        }
        attachmentRepository.delete(a);
    }

    private boolean canDelete(Attachment a, IssueFlowUserDetails currentUser) {
        if (currentUser == null) return false;
        if (currentUser.getRole() == Role.ADMIN) return true;
        return a.getUploadedBy() != null
            && a.getUploadedBy().getId().equals(currentUser.getUserId());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new PayloadTooLargeException("File exceeds the 10 MB limit");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_MIME.contains(type)) {
            throw new UnsupportedMimeTypeException(
                "Unsupported MIME type: " + type + ". Allowed: " + ALLOWED_MIME);
        }
    }

    static AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(
            a.getId(),
            a.getTicket().getId(),
            a.getFilename(),
            a.getContentType(),
            a.getSizeBytes(),
            a.getUploadedAt());
    }
}
