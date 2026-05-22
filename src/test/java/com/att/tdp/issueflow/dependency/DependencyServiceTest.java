package com.att.tdp.issueflow.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnprocessableEntityException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins down dependency-graph invariants: no self-edges, same-project only, no duplicates,
 * no cycles. Repositories are mocked so the test is deterministic and independent of JPA.
 */
@ExtendWith(MockitoExtension.class)
class DependencyServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TicketDependencyRepository dependencyRepository;
    @InjectMocks DependencyService service;

    private Ticket ticket(Long id, Long projectId) {
        return Ticket.builder()
            .id(id)
            .title("t" + id)
            .status(TicketStatus.TODO)
            .priority(TicketPriority.MEDIUM)
            .project(Project.builder().id(projectId).name("p").build())
            .build();
    }

    @Test
    void addRejectsSelfDependency() {
        assertThatThrownBy(() -> service.add(7L, 7L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("itself");
        verify(ticketRepository, never()).findById(any());
    }

    @Test
    void addThrowsNotFoundWhenTicketMissing() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addThrowsNotFoundWhenBlockerMissing() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addRejectsCrossProjectEdge() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, 2L)));

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("same project");
    }

    @Test
    void addRejectsDuplicateEdge() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, 1L)));
        when(dependencyRepository.existsById_TicketIdAndId_BlockedByTicketId(7L, 8L))
            .thenReturn(true);

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("already blocked");
    }

    @Test
    void addRejectsDirectCycle() {
        // Existing edge: 8 → 7 (8 is blocked by 7). Adding 7 → 8 would close the cycle.
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, 1L)));
        when(dependencyRepository.findBlockerIds(8L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void addRejectsTransitiveCycle() {
        // 8 → 9 → 7. Adding 7 → 8 would close the loop through 9.
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, 1L)));
        when(dependencyRepository.findBlockerIds(8L)).thenReturn(List.of(9L));
        when(dependencyRepository.findBlockerIds(9L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.add(7L, 8L))
            .isInstanceOf(UnprocessableEntityException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void addPersistsEdgeWhenAllChecksPass() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, 1L)));
        when(ticketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, 1L)));

        service.add(7L, 8L);

        ArgumentCaptor<TicketDependency> captor = ArgumentCaptor.forClass(TicketDependency.class);
        verify(dependencyRepository).save(captor.capture());
        TicketDependency saved = captor.getValue();
        assertThat(saved.getId().getTicketId()).isEqualTo(7L);
        assertThat(saved.getId().getBlockedByTicketId()).isEqualTo(8L);
    }

    @Test
    void removeDeletesExistingEdge() {
        when(dependencyRepository.existsById(new TicketDependencyId(7L, 8L))).thenReturn(true);

        service.remove(7L, 8L);

        verify(dependencyRepository).deleteById(new TicketDependencyId(7L, 8L));
    }

    @Test
    void removeThrowsNotFoundForUnknownEdge() {
        when(dependencyRepository.existsById(new TicketDependencyId(7L, 8L))).thenReturn(false);

        assertThatThrownBy(() -> service.remove(7L, 8L))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(dependencyRepository, never()).deleteById(any(TicketDependencyId.class));
    }

    @Test
    void findBlockersThrowsNotFoundForUnknownTicket() {
        when(ticketRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBlockers(7L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
