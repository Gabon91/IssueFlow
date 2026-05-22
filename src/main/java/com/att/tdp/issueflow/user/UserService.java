package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.audit.Audited;
import com.att.tdp.issueflow.common.error.ConflictException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnprocessableEntityException;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.user.dto.UserCreateRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import com.att.tdp.issueflow.user.dto.UserUpdateRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the User aggregate. Enforces username/email uniqueness and hashes
 * passwords with BCrypt before persistence. Update is a partial PATCH; only non-null fields
 * are applied.
 *
 * <p>Delete is a soft-delete: {@code deletedAt} is stamped and the user becomes invisible to
 * default reads (and to authentication) thanks to {@code @SQLRestriction} on the entity.
 * Three safeguards apply: callers cannot delete themselves, the last live ADMIN cannot be
 * deleted, and a user with any open (non-DONE) assigned tickets is blocked until reassigned.
 * Restore is ADMIN-only and clears the timestamp.</p>
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       TicketRepository ticketRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
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

    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.USER)
    public UserResponse create(UserCreateRequest req) {
        if (userRepository.existsByUsernameIncludingDeleted(req.username())) {
            throw new ConflictException("Username already in use: " + req.username());
        }
        if (userRepository.existsByEmailIncludingDeleted(req.email())) {
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

    @Audited(action = AuditAction.UPDATE, entityType = AuditEntityType.USER, idArg = 0)
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

    @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.USER, idArg = 0)
    public void delete(Long id) {
        User user = load(id);
        Long current = currentUserId();
        if (current != null && current.equals(id)) {
            throw new UnprocessableEntityException("Users cannot delete themselves");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new UnprocessableEntityException("Cannot delete the last active ADMIN");
        }
        long openTickets = ticketRepository.countByAssigneeIdAndStatusNot(id, TicketStatus.DONE);
        if (openTickets > 0) {
            throw new ConflictException("User has " + openTickets
                + " open ticket(s); reassign them before deleting");
        }
        user.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findDeleted() {
        return userRepository.findDeleted().stream().map(userMapper::toResponse).toList();
    }

    @Audited(action = AuditAction.RESTORE, entityType = AuditEntityType.USER, idArg = 0)
    public void restore(Long id) {
        User user = userRepository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> ResourceNotFoundException.of("User", id));
        if (user.getDeletedAt() == null) {
            return;
        }
        userRepository.restore(id);
    }

    private User load(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof IssueFlowUserDetails iud ? iud.getUserId() : null;
    }
}
