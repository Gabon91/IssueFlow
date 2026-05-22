package com.att.tdp.issueflow.attachment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.common.error.UnsupportedMimeTypeException;
import com.att.tdp.issueflow.common.security.JwtAuthenticationFilter;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Web slice for AttachmentController: multipart upload mapping, 415 envelope, download headers. */
@WebMvcTest(controllers = AttachmentController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AttachmentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AttachmentService attachmentService;

    @Test
    void uploadReturnsStoredMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "screenshot.png",
            "image/png", new byte[] {1, 2, 3});
        when(attachmentService.upload(eq(7L), any(), any()))
            .thenReturn(new AttachmentResponse(11L, 7L, "screenshot.png",
                "image/png", 3L, Instant.parse("2026-01-01T00:00:00Z")));

        mvc.perform(multipart("/tickets/{ticketId}/attachments", 7L).file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.ticketId").value(7))
            .andExpect(jsonPath("$.filename").value("screenshot.png"))
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.sizeBytes").value(3));
    }

    @Test
    void uploadOfUnsupportedMimeMapsToFourHundredFifteen() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe",
            "application/octet-stream", new byte[] {0});
        when(attachmentService.upload(eq(7L), any(), any()))
            .thenThrow(new UnsupportedMimeTypeException("Unsupported MIME type: application/octet-stream"));

        mvc.perform(multipart("/tickets/{ticketId}/attachments", 7L).file(file))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported MIME")));
    }

    @Test
    void downloadStreamsAttachmentBytesWithDispositionHeader() throws Exception {
        Ticket t = Ticket.builder().id(7L).title("t").build();
        Attachment a = Attachment.builder().id(11L).ticket(t)
            .filename("notes.txt").contentType("text/plain")
            .sizeBytes(5L).data("hello".getBytes()).build();
        when(attachmentService.download(7L, 11L)).thenReturn(a);

        mvc.perform(get("/tickets/{ticketId}/attachments/{attachmentId}/download", 7L, 11L))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_PLAIN))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("notes.txt")))
            .andExpect(content().bytes("hello".getBytes()));
    }
}
