package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.TokenResponse;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.common.security.JwtService;
import com.att.tdp.issueflow.common.security.TokenDenyList;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Authentication workflow: credential check via Spring Security's {@link AuthenticationManager},
 * JWT issuance, and revocation through the {@link TokenDenyList}.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenDenyList denyList;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenDenyList denyList) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.denyList = denyList;
    }

    public TokenResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        IssueFlowUserDetails principal = (IssueFlowUserDetails) auth.getPrincipal();
        JwtService.IssuedToken issued = jwtService.issue(
            principal.getUserId(), principal.getUsername(), principal.getRole().name());
        return new TokenResponse(issued.token(), "Bearer", jwtService.expirationSeconds());
    }

    public void logout(String jti, Instant expiresAt) {
        if (jti != null && expiresAt != null) {
            denyList.revoke(jti, expiresAt);
        }
    }
}
