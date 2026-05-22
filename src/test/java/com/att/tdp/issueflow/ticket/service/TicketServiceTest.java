package com.att.tdp.issueflow.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.error.BlockedByDependencyException;
import com.att.tdp.issueflow.common.error.IllegalStateTransitionException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.api.TicketMapper;
import com.att.tdp.issueflow.ticket.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins down service-level invariants I1-I3 with mocked repositories: forward-only state
 * transitions, immutability of DONE tickets, and the blocker guard before DONE.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock TicketMapper ticketMapper;
    TicketService service;

    private Ticket ticketInStatus(TicketStatus status) {
        return Ticket.builder()
            .id(7L)
            .title("t").status(status)
            .priority(TicketPriority.MEDIUM)
            .build();
    }

    @BeforeEach
    void mapperStubReturnsNull() {
        service = new TicketService(ticketRepository, projectRepository, userRepository,
            ticketMapper, new SimpleMeterRegistry());
        // We don't assert mapper output; lenient stub keeps strict-mode Mockito quiet.
        lenient().when(ticketMapper.toResponse(any())).thenReturn(null);
    }

    @Test
    void updateRejectsIllegalForwardSkip() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticketInStatus(TicketStatus.TODO)));
        TicketUpdateRequest req = new TicketUpdateRequest(null, null, TicketStatus.IN_REVIEW, null, null, null);

        assertThatThrownBy(() -> service.update(7L, req))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void updateRejectsBackwardTransition() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticketInStatus(TicketStatus.IN_REVIEW)));
        TicketUpdateRequest req = new TicketUpdateRequest(null, null, TicketStatus.TODO, null, null, null);

        assertThatThrownBy(() -> service.update(7L, req))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void doneTicketsAreImmutable() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticketInStatus(TicketStatus.DONE)));
        // Any field change must be rejected, even a no-op title set.
        TicketUpdateRequest req = new TicketUpdateRequest("new title", null, null, null, null, null);

        assertThatThrownBy(() -> service.update(7L, req))
            .isInstanceOf(IllegalStateTransitionException.class)
            .hasMessageContaining("DONE");
    }

    @Test
    void transitionToDoneIsBlockedWhenOpenBlockersExist() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticketInStatus(TicketStatus.IN_REVIEW)));
        when(ticketRepository.countOpenBlockers(7L)).thenReturn(2L);
        TicketUpdateRequest req = new TicketUpdateRequest(null, null, TicketStatus.DONE, null, null, null);

        assertThatThrownBy(() -> service.update(7L, req))
            .isInstanceOf(BlockedByDependencyException.class);
    }

    @Test
    void transitionToDoneSucceedsWhenNoOpenBlockers() {
        Ticket t = ticketInStatus(TicketStatus.IN_REVIEW);
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(t));
        when(ticketRepository.countOpenBlockers(7L)).thenReturn(0L);
        TicketUpdateRequest req = new TicketUpdateRequest(null, null, TicketStatus.DONE, null, null, null);

        service.update(7L, req);

        assertThat(t.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void updateThrowsNotFoundWhenTicketMissing() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.empty());
        TicketUpdateRequest req = new TicketUpdateRequest("x", null, null, null, null, null);

        assertThatThrownBy(() -> service.update(7L, req))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketRepository, never()).countOpenBlockers(any());
    }
}
