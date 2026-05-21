package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /users}. */
public record UserCreateRequest(
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_]+$")
    String username,

    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 1, max = 120)
    String fullName,

    @NotNull
    Role role,

    @NotBlank
    @Size(min = 8, max = 100)
    String password
) {}
