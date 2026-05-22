package com.att.tdp.issueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.dto.WorkloadEntry;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserWorkload;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Covers the workload reporting endpoint: project existence check, mapping and ordering. */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock ProjectMapper projectMapper;
    @InjectMocks ProjectService service;

    private UserWorkload row(long userId, String username, long openTickets) {
        UserWorkload w = mock(UserWorkload.class);
        when(w.getUserId()).thenReturn(userId);
        when(w.getUsername()).thenReturn(username);
        when(w.getOpenTicketCount()).thenReturn(openTickets);
        return w;
    }

    @Test
    void workloadThrowsNotFoundForUnknownProject() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.workload(99L))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void workloadReturnsEmptyListWhenNoDevelopers() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(mock(Project.class)));
        when(userRepository.findDeveloperWorkloadsByProject(1L)).thenReturn(List.of());

        List<WorkloadEntry> result = service.workload(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void workloadPreservesRepositoryOrderAndMapsFields() {
        // Repository already orders ascending by openTicketCount, then id. The service must
        // pass that order through verbatim and project the fields exposed in openapi.yaml.
        // Build the projection mocks first so nested when() calls don't trip Mockito's
        // unfinished-stubbing detector.
        UserWorkload r1 = row(7L, "alice", 0L);
        UserWorkload r2 = row(8L, "bob",   2L);
        UserWorkload r3 = row(9L, "carol", 5L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(mock(Project.class)));
        when(userRepository.findDeveloperWorkloadsByProject(1L)).thenReturn(List.of(r1, r2, r3));

        List<WorkloadEntry> result = service.workload(1L);

        assertThat(result)
            .extracting(WorkloadEntry::userId, WorkloadEntry::username, WorkloadEntry::openTicketCount)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(7L, "alice", 0L),
                org.assertj.core.groups.Tuple.tuple(8L, "bob",   2L),
                org.assertj.core.groups.Tuple.tuple(9L, "carol", 5L)
            );
        verify(userRepository).findDeveloperWorkloadsByProject(1L);
    }
}
