package com.att.tdp.issueflow.common.security;

import com.att.tdp.issueflow.user.Role;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates the {@code Authorization: Bearer <jwt>} header on every request.
 * Tokens revoked via {@link TokenDenyList} (logout) are rejected. The chain proceeds with an
 * empty {@code SecurityContext} when no/invalid header is present — endpoint-level rules
 * decide whether to allow that anonymous access.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenyList denyList;

    public JwtAuthenticationFilter(JwtService jwtService, TokenDenyList denyList) {
        this.jwtService = jwtService;
        this.denyList = denyList;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                JwtService.ParsedToken parsed = jwtService.parse(token);
                if (parsed.jti() != null && denyList.isRevoked(parsed.jti())) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                    return;
                }
                IssueFlowUserDetails principal = new IssueFlowUserDetails(
                    parsed.userId(), parsed.username(), Role.valueOf(parsed.role()));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, token, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                request.setAttribute("jwt.jti", parsed.jti());
                request.setAttribute("jwt.exp", parsed.expiresAt());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
