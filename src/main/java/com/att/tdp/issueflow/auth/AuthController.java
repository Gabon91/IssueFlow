package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.TokenResponse;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.security.CurrentUser;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserMapper;
import com.att.tdp.issueflow.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for authentication: login, logout, current-user lookup. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserRepository userRepository, UserMapper userMapper) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        return authService.login(body);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        Object jti = request.getAttribute("jwt.jti");
        Object exp = request.getAttribute("jwt.exp");
        if (jti instanceof String s && exp instanceof Instant i) {
            authService.logout(s, i);
        }
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser IssueFlowUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
            .map(userMapper::toResponse)
            .orElseThrow(() -> ResourceNotFoundException.of("User", currentUser.getUserId()));
    }
}
