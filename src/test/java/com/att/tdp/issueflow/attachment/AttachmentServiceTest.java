package com.att.tdp.issueflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.common.error.PayloadTooLargeException;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.common.error.UnsupportedMimeTypeException;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

/** Covers upload validation (MIME + size), ownership checks on delete, and not-found paths. */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock AttachmentRepository attachmentRepository;
    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AttachmentService service;

    private Ticket ticket(long id) { return Ticket.builder().id(id).title("t").build(); }
    private User user(long id, Role role) {
        return User.builder().id(id).username("u" + id).role(role).build();
    }
    private IssueFlowUserDetails principal(long id, Role role) {
        return new IssueFlowUserDetails(id, "u" + id, role);
    }

    @Test
    void uploadRejectsDisallowedMimeAs415() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));
        MockMultipartFile bad = new MockMultipartFile("file", "evil.exe",
            "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(1L, bad, principal(7L, Role.DEVELOPER)))
            .isInstanceOf(UnsupportedMimeTypeException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFileOverTenMegabytesAs413() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));
        byte[] huge = new byte[(int) AttachmentService.MAX_FILE_BYTES + 1];
        MockMultipartFile big = new MockMultipartFile("file", "huge.pdf",
            "application/pdf", huge);

        assertThatThrownBy(() -> service.upload(1L, big, principal(7L, Role.DEVELOPER)))
            .isInstanceOf(PayloadTooLargeException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadPersistsAllowedMimeAndReturnsResponse() {
        Ticket t = ticket(1L);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(t));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, Role.DEVELOPER)));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(42L);
            return a;
        });
        MockMultipartFile good = new MockMultipartFile("file", "screenshot.png",
            "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

        AttachmentResponse resp = service.upload(1L, good, principal(7L, Role.DEVELOPER));

        assertThat(resp.id()).isEqualTo(42L);
        assertThat(resp.ticketId()).isEqualTo(1L);
        assertThat(resp.filename()).isEqualTo("screenshot.png");
        assertThat(resp.contentType()).isEqualTo("image/png");
        assertThat(resp.sizeBytes()).isEqualTo(4L);
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getUploadedBy().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getData()).hasSize(4);
    }

    @Test
    void uploadFailsForUnknownTicket() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile good = new MockMultipartFile("file", "a.pdf",
            "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(99L, good, principal(7L, Role.DEVELOPER)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteForbidsNonUploaderNonAdmin() {
        Attachment a = Attachment.builder().id(5L).ticket(ticket(1L))
            .uploadedBy(user(7L, Role.DEVELOPER)).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.delete(1L, 5L, principal(8L, Role.DEVELOPER)))
            .isInstanceOf(AccessDeniedException.class);
        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void deleteAllowsUploader() {
        Attachment a = Attachment.builder().id(5L).ticket(ticket(1L))
            .uploadedBy(user(7L, Role.DEVELOPER)).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        service.delete(1L, 5L, principal(7L, Role.DEVELOPER));

        verify(attachmentRepository).delete(a);
    }

    @Test
    void deleteAllowsAdmin() {
        Attachment a = Attachment.builder().id(5L).ticket(ticket(1L))
            .uploadedBy(user(7L, Role.DEVELOPER)).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        service.delete(1L, 5L, principal(99L, Role.ADMIN));

        verify(attachmentRepository).delete(a);
    }

    @Test
    void deleteFailsWhenAttachmentBelongsToOtherTicket() {
        Attachment a = Attachment.builder().id(5L).ticket(ticket(2L))
            .uploadedBy(user(7L, Role.DEVELOPER)).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.delete(1L, 5L, principal(7L, Role.DEVELOPER)))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
