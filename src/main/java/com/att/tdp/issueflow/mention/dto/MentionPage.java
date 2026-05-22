package com.att.tdp.issueflow.mention.dto;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import java.util.List;

/**
 * Paged response for {@code GET /users/{userId}/mentions}. Shape mirrors the
 * {@code MentionPage} schema in {@code openapi.yaml}: a slice of {@link CommentResponse}
 * items together with the total mention count and the 1-based page index that was returned.
 */
public record MentionPage(
    List<CommentResponse> data,
    long total,
    int page
) {}
