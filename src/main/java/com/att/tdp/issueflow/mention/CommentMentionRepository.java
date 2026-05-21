package com.att.tdp.issueflow.mention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link CommentMention}. Backs the GET /mentions inbox endpoint
 * and lets the mention parser short-circuit when a (comment, user) pair already exists
 * (the table's unique constraint would otherwise raise on duplicates).
 */
@Repository
public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    Page<CommentMention> findByMentionedUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByCommentIdAndMentionedUserId(Long commentId, Long mentionedUserId);

    long countByMentionedUserId(Long userId);
}
