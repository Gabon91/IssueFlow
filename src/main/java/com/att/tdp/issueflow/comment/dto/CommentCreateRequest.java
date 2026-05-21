package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /tickets/{ticketId}/comments}. */
public record CommentCreateRequest(
    @NotNull Long authorId,
    @NotBlank @Size(min = 1, max = 5000) String content
) {}
