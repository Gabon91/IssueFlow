package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.ticket.domain.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
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
 * Directed "is-blocked-by" edge between two tickets. The service layer prevents cycles
 * and enforces the rule that a {@code DONE} transition is rejected while any blocker is open.
 */
@Entity
@Table(
    name = "ticket_dependencies",
    indexes = {
        @Index(name = "ix_deps_ticket", columnList = "ticket_id"),
        @Index(name = "ix_deps_blocker", columnList = "blocked_by_ticket_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TicketDependency {

    @EmbeddedId
    private TicketDependencyId id;

    @MapsId("ticketId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_deps_ticket"))
    private Ticket ticket;

    @MapsId("blockedByTicketId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_by_ticket_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_deps_blocker"))
    private Ticket blockedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
