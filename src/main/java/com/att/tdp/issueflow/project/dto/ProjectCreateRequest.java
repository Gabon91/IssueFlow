package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /projects}. */
public record ProjectCreateRequest(
    @NotBlank @Size(min = 1, max = 120) String name,
    @Size(max = 2000) String description,
    @NotNull Long ownerId
) {}
