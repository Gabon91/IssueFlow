package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.common.security.CurrentUser;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for ticket attachments. POST and DELETE follow the OpenAPI contract; the GET
 * list and binary download endpoints round out the slice so clients can actually consume
 * what they uploaded.
 */
@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable Long ticketId) {
        return attachmentService.listByTicket(ticketId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(@PathVariable Long ticketId,
                                     @RequestParam("file") MultipartFile file,
                                     @CurrentUser IssueFlowUserDetails currentUser) {
        return attachmentService.upload(ticketId, file, currentUser);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long ticketId,
                                                      @PathVariable Long attachmentId) {
        Attachment a = attachmentService.download(ticketId, attachmentId);
        ByteArrayResource body = new ByteArrayResource(a.getData());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(a.getContentType()))
            .contentLength(a.getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + a.getFilename() + "\"")
            .body(body);
    }

    @DeleteMapping("/{attachmentId}")
    public void delete(@PathVariable Long ticketId,
                       @PathVariable Long attachmentId,
                       @CurrentUser IssueFlowUserDetails currentUser) {
        attachmentService.delete(ticketId, attachmentId, currentUser);
    }
}
