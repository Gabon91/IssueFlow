package com.att.tdp.issueflow.auth.dto;

/** JWT envelope returned by {@code POST /auth/login}. */
public record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
