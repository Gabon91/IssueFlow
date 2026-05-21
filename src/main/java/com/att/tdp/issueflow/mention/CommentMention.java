package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.Comment;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

/**
 * Association row recording that a {@link Comment} mentions a {@link User}.
 * Created by the {@code MentionParser} as part of the same transaction that persists the comment.
 * Powers the GET /mentions inbox endpoint.
 */
@Entity
@Table(
    name = "comment_mentions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mentions_comment_user",
        columnNames = {"comment_id", "mentioned_user_id"}
    ),
    indexes = {
        @Index(name = "ix_mentions_user", columnList = "mentioned_user_id"),
        @Index(name = "ix_mentions_comment", columnList = "comment_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommentMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_mentions_comment"))
    private Comment comment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_mentions_user"))
    private User mentionedUser;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
