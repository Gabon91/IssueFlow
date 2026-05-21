package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.domain.BaseAuditableEntity;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-text comment attached to a ticket. Mentions of the form {@code @username} are
 * extracted by the service layer and persisted as {@link com.att.tdp.issueflow.mention.CommentMention}
 * rows for fast lookup.
 */
@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "ix_comments_ticket", columnList = "ticket_id"),
        @Index(name = "ix_comments_author", columnList = "author_id")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Comment extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_comments_ticket"))
    private Ticket ticket;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_comments_author"))
    private User author;

    @NotBlank
    @Size(min = 1, max = 5000)
    @Column(nullable = false, length = 5000)
    private String content;

    @Version
    @Column(nullable = false)
    private long version;
}
