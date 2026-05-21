package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Binary file uploaded against a ticket. The 10 MB limit and MIME allow-list are enforced
 * at the service layer; this entity stores the bytes inline. For larger deployments the
 * {@link #data} field can be migrated to an object store and replaced with a URI.
 */
@Entity
@Table(
    name = "attachments",
    indexes = @Index(name = "ix_attachments_ticket", columnList = "ticket_id")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_attachments_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by",
        foreignKey = @ForeignKey(name = "fk_attachments_uploader"))
    private User uploadedBy;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String filename;

    @NotBlank
    @Size(max = 100)
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}
