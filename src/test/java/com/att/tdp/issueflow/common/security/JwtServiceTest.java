package com.att.tdp.issueflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

/** Round-trip and tamper-detection tests for the HS256 JWT issuer/parser. */
class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32b!";
    private static final String OTHER_SECRET = "OTHER-secret-OTHER-secret-OTHER-secret-32";

    private JwtService newService(String secret, long ttlSeconds) {
        return new JwtService(new JwtProperties(secret, "issueflow", "issueflow-api", ttlSeconds));
    }

    @Test
    void issueThenParseRoundTripsAllClaims() {
        JwtService svc = newService(SECRET, 3600);
        JwtService.IssuedToken issued = svc.issue(42L, "alice", "DEVELOPER");

        JwtService.ParsedToken parsed = svc.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.role()).isEqualTo("DEVELOPER");
        assertThat(parsed.jti()).isEqualTo(issued.jti());
        // JWT `exp` is stored at second precision per RFC 7519, so compare epoch seconds.
        assertThat(parsed.expiresAt().getEpochSecond()).isEqualTo(issued.expiresAt().getEpochSecond());
    }

    @Test
    void parseRejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = newService(SECRET, 3600);
        JwtService verifier = newService(OTHER_SECRET, 3600);
        String foreignToken = issuer.issue(1L, "bob", "ADMIN").token();

        assertThatThrownBy(() -> verifier.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsExpiredToken() {
        JwtService svc = newService(SECRET, 0); // already expired on issue
        String token = svc.issue(1L, "ed", "DEVELOPER").token();

        assertThatThrownBy(() -> svc.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsTamperedPayload() {
        JwtService svc = newService(SECRET, 3600);
        String token = svc.issue(1L, "ed", "DEVELOPER").token();
        // Mutate the middle (payload) segment by appending a character.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "X." + parts[2];

        assertThatThrownBy(() -> svc.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
