package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.Size;

/** Partial update for {@code PATCH /projects/{projectId}}. {@code null} means "do not change". */
public record ProjectUpdateRequest(
    @Size(min = 1, max = 120) String name,
    @Size(max = 2000) String description
) {}
