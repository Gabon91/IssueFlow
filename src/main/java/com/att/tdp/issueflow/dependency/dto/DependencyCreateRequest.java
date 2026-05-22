package com.att.tdp.issueflow.dependency.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /tickets/{ticketId}/dependencies}. */
public record DependencyCreateRequest(
    @NotNull Long blockedBy
) {}
