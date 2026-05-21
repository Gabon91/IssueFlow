package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;

/** Response body for user-returning endpoints. Mirrors {@code UserResponse} in {@code openapi.yaml}. */
public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Role role
) {}
