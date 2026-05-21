package com.att.tdp.issueflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** Verifies the in-memory revocation cache used by AuthService.logout. */
class TokenDenyListTest {

    @Test
    void returnsFalseForUnknownJti() {
        TokenDenyList list = new TokenDenyList();
        assertThat(list.isRevoked("anything")).isFalse();
    }

    @Test
    void returnsTrueAfterRevoke() {
        TokenDenyList list = new TokenDenyList();
        Instant exp = Instant.now().plus(1, ChronoUnit.HOURS);
        list.revoke("jti-1", exp);
        assertThat(list.isRevoked("jti-1")).isTrue();
    }

    @Test
    void entryExpiresOnceTokenExpiryHasPassed() {
        TokenDenyList list = new TokenDenyList();
        list.revoke("jti-expired", Instant.now().minusSeconds(1));
        // Caffeine evicts lazily on next access; the entry should already be gone.
        assertThat(list.isRevoked("jti-expired")).isFalse();
    }
}
