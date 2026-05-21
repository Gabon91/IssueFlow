package com.att.tdp.issueflow.ticket.domain;

import com.att.tdp.issueflow.common.domain.BaseAuditableEntity;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * Core work item. The state-machine, blocker rules and auto-assignment policies are enforced
 * in the service layer; the entity itself only carries data, the optimistic-lock counter and
 * the soft-delete marker.
 */
@Entity
@Table(
    name = "tickets",
    indexes = {
        @Index(name = "ix_tickets_project", columnList = "project_id"),
        @Index(name = "ix_tickets_assignee", columnList = "assignee_id"),
        @Index(name = "ix_tickets_status", columnList = "status"),
        @Index(name = "ix_tickets_due_date", columnList = "due_date"),
        @Index(name = "ix_tickets_deleted_at", columnList = "deleted_at")
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Ticket extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @Size(max = 10000)
    @Column(length = 10000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketType type;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id",
        foreignKey = @ForeignKey(name = "fk_tickets_assignee"))
    private User assignee;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "is_overdue", nullable = false)
    private boolean isOverdue;

    /** Soft-delete marker; {@code null} for live rows. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Optimistic-lock counter; concurrent updates surface as HTTP 409. */
    @Version
    @Column(nullable = false)
    private long version;
}
