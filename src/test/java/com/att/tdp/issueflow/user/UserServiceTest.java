package com.att.tdp.issueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.error.ConflictException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnprocessableEntityException;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.user.dto.UserCreateRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Locks the uniqueness invariants enforced by UserService.create. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock TicketRepository ticketRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    private UserCreateRequest req() {
        return new UserCreateRequest("alice", "alice@example.com", "Alice", Role.DEVELOPER, "password1");
    }

    @Test
    void rejectsDuplicateUsernameBeforeHashing() {
        when(userRepository.existsByUsernameIncludingDeleted("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req())).isInstanceOf(ConflictException.class);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateEmailBeforeHashing() {
        when(userRepository.existsByUsernameIncludingDeleted("alice")).thenReturn(false);
        when(userRepository.existsByEmailIncludingDeleted("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req())).isInstanceOf(ConflictException.class);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(long id, Role role) {
        User u = User.builder().username("u" + id).email(id + "@e.com").fullName("U" + id)
            .role(role).passwordHash("h").build();
        u.setId(id);
        return u;
    }

    private void authAs(long userId) {
        IssueFlowUserDetails p = new IssueFlowUserDetails(userId, "actor", Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(p, "t", p.getAuthorities()));
    }

    @Test
    void deleteRejectsSelfDeletion() {
        User self = user(42L, Role.ADMIN);
        when(userRepository.findById(42L)).thenReturn(Optional.of(self));
        authAs(42L);

        assertThatThrownBy(() -> service.delete(42L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("cannot delete themselves");
        assertThat(self.getDeletedAt()).isNull();
    }

    @Test
    void deleteRejectsLastAdmin() {
        User lastAdmin = user(7L, Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);
        authAs(99L);

        assertThatThrownBy(() -> service.delete(7L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("last active ADMIN");
        assertThat(lastAdmin.getDeletedAt()).isNull();
    }

    @Test
    void deleteRejectsWhenUserHasOpenTickets() {
        User dev = user(3L, Role.DEVELOPER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(dev));
        when(ticketRepository.countByAssigneeIdAndStatusNot(eq(3L), eq(TicketStatus.DONE))).thenReturn(2L);
        authAs(99L);

        assertThatThrownBy(() -> service.delete(3L))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("open ticket");
        assertThat(dev.getDeletedAt()).isNull();
    }

    @Test
    void deleteStampsDeletedAtWhenSafe() {
        User dev = user(4L, Role.DEVELOPER);
        when(userRepository.findById(4L)).thenReturn(Optional.of(dev));
        when(ticketRepository.countByAssigneeIdAndStatusNot(eq(4L), eq(TicketStatus.DONE))).thenReturn(0L);
        authAs(99L);

        service.delete(4L);

        assertThat(dev.getDeletedAt()).isNotNull();
    }

    @Test
    void restoreFailsWhenUserUnknown() {
        when(userRepository.findByIdIncludingDeleted(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(123L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restoreNoOpsWhenAlreadyActive() {
        User active = user(5L, Role.DEVELOPER);
        when(userRepository.findByIdIncludingDeleted(5L)).thenReturn(Optional.of(active));

        service.restore(5L);

        verify(userRepository, never()).restore(any());
    }

    @Test
    void restoreClearsDeletedAt() {
        User deleted = user(6L, Role.DEVELOPER);
        deleted.setDeletedAt(Instant.now());
        when(userRepository.findByIdIncludingDeleted(6L)).thenReturn(Optional.of(deleted));

        service.restore(6L);

        verify(userRepository).restore(6L);
    }
}
