package com.att.tdp.issueflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and parses stateless HS256 JWTs. Tokens carry the username as subject,
 * the user id and role as claims, and a unique {@code jti} so the {@link TokenDenyList}
 * can revoke individual tokens on logout.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Builds and signs a token. Returns the compact JWS plus the issued/expiry instants. */
    public IssuedToken issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.expirationSeconds());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
            .issuer(props.issuer())
            .audience().add(props.audience()).and()
            .subject(username)
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim(CLAIM_USER_ID, userId)
            .claim(CLAIM_ROLE, role)
            .signWith(key, Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, jti, now, exp);
    }

    /** Parses and verifies the signature. Throws {@link JwtException} on any failure. */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(props.issuer())
            .requireAudience(props.audience())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        Object uid = claims.get(CLAIM_USER_ID);
        Long userId = (uid instanceof Number n) ? n.longValue() : null;
        String role = claims.get(CLAIM_ROLE, String.class);
        return new ParsedToken(claims.getId(), userId, claims.getSubject(), role, claims.getExpiration().toInstant());
    }

    public long expirationSeconds() {
        return props.expirationSeconds();
    }

    public record IssuedToken(String token, String jti, Instant issuedAt, Instant expiresAt) {}

    public record ParsedToken(String jti, Long userId, String username, String role, Instant expiresAt) {}
}
