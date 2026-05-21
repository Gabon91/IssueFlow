package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.error.ConflictException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.user.dto.UserCreateRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import com.att.tdp.issueflow.user.dto.UserUpdateRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the User aggregate. Enforces username/email uniqueness and hashes
 * passwords with BCrypt before persistence. Update is a partial PATCH; only non-null fields
 * are applied.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(load(id));
    }

    public UserResponse create(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ConflictException("Username already in use: " + req.username());
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already in use: " + req.email());
        }
        User user = User.builder()
            .username(req.username())
            .email(req.email())
            .fullName(req.fullName())
            .role(req.role())
            .passwordHash(passwordEncoder.encode(req.password()))
            .build();
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse update(Long id, UserUpdateRequest req) {
        User user = load(id);
        if (req.fullName() != null) {
            user.setFullName(req.fullName());
        }
        if (req.role() != null) {
            user.setRole(req.role());
        }
        return userMapper.toResponse(user);
    }

    public void delete(Long id) {
        User user = load(id);
        userRepository.delete(user);
    }

    private User load(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
