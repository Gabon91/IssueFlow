package com.att.tdp.issueflow.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code issueflow.security.jwt.*} from configuration.
 * The secret must be at least 32 bytes for HS256.
 */
@ConfigurationProperties(prefix = "issueflow.security.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    String audience,
    long expirationSeconds
) {}
