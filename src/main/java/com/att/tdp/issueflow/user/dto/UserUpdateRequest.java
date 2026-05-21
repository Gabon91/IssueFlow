package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /users/update/{userId}}. All fields optional;
 * {@code null} means "do not change".
 */
public record UserUpdateRequest(
    @Size(min = 1, max = 120) String fullName,
    Role role
) {}
