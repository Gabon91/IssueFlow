package com.att.tdp.issueflow.ticket.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.csv.dto.ImportSummary;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.ticket.domain.TicketType;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

/** Covers CSV header validation, per-row failure isolation, enum/assignee parsing and export streaming. */
@ExtendWith(MockitoExtension.class)
class TicketCsvServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    TicketCsvService service;

    @BeforeEach
    void setUp() {
        service = new TicketCsvService(ticketRepository, projectRepository, userRepository,
            new SimpleMeterRegistry());
    }

    private Project project(long id) {
        return Project.builder().id(id).name("P" + id)
            .owner(User.builder().id(99L).username("o").role(Role.ADMIN).build()).build();
    }
    private User user(long id) { return User.builder().id(id).username("u" + id).role(Role.DEVELOPER).build(); }
    private Ticket ticket(long id, Project p, User a) {
        return Ticket.builder().id(id).title("T" + id).description("d")
            .status(TicketStatus.TODO).priority(TicketPriority.MEDIUM).type(TicketType.BUG)
            .project(p).assignee(a).isOverdue(false).build();
    }

    private MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "in.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importRejectsMissingProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.importFromCsv(1L, csv("title\nfoo")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void importRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> service.importFromCsv(1L, empty))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importRejectsMissingRequiredColumns() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        assertThatThrownBy(() -> service.importFromCsv(1L, csv("title,status\nfoo,TODO")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required columns");
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void importCollectsErrorsAndPersistsValidRows() {
        Project p = project(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        String csvBody = """
            title,description,status,priority,type,assigneeId
            Good,one,TODO,LOW,BUG,2
            ,bad-title,TODO,LOW,BUG,
            Bad,bad-status,OPEN,LOW,BUG,
            Ghost,bad-assignee,TODO,LOW,BUG,42
            Solo,no-assignee,IN_PROGRESS,HIGH,FEATURE,
            """;
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ImportSummary summary = service.importFromCsv(1L, csv(csvBody));

        assertThat(summary.created()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(3);
        assertThat(summary.errors()).extracting("row").containsExactly(3L, 4L, 5L);
        assertThat(summary.errors().get(0).message()).contains("title is required");
        assertThat(summary.errors().get(1).message()).contains("Invalid status value \"OPEN\"");
        assertThat(summary.errors().get(2).message()).contains("Unknown assigneeId: 42");
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        List<Ticket> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getTitle()).isEqualTo("Good");
        assertThat(saved.get(0).getAssignee().getId()).isEqualTo(2L);
        assertThat(saved.get(1).getTitle()).isEqualTo("Solo");
        assertThat(saved.get(1).getAssignee()).isNull();
        assertThat(saved.get(1).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void exportRejectsMissingProject() {
        when(projectRepository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.exportToCsv(7L, new StringWriter()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportStreamsHeaderAndRows() throws Exception {
        Project p = project(1L);
        User dev = user(2L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p));
        when(ticketRepository.findByProjectId(eq(1L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                ticket(10L, p, dev),
                ticket(11L, p, null))));

        StringWriter out = new StringWriter();
        service.exportToCsv(1L, out);

        String[] lines = out.toString().split("\r\n|\n");
        assertThat(lines[0]).isEqualTo("id,title,description,status,priority,type,assigneeId");
        assertThat(lines[1]).isEqualTo("10,T10,d,TODO,MEDIUM,BUG,2");
        assertThat(lines[2]).isEqualTo("11,T11,d,TODO,MEDIUM,BUG,");
    }
}
