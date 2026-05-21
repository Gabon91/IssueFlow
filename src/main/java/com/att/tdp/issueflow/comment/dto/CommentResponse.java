package com.att.tdp.issueflow.comment.dto;

import java.time.Instant;
import java.util.List;

/** Response body for comment-returning endpoints. */
public record CommentResponse(
    Long id,
    Long ticketId,
    Long authorId,
    String content,
    List<MentionedUser> mentionedUsers,
    Instant createdAt,
    Instant updatedAt
) {}
