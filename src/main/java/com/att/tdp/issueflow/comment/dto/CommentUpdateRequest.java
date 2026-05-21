package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /tickets/{ticketId}/comments/{commentId}}. */
public record CommentUpdateRequest(
    @NotBlank @Size(min = 1, max = 5000) String content
) {}
