package com.att.tdp.issueflow.attachment.dto;

import java.time.Instant;

/** Metadata returned for stored attachments. Mirrors {@code AttachmentResponse} in {@code openapi.yaml}. */
public record AttachmentResponse(
    Long id,
    Long ticketId,
    String filename,
    String contentType,
    long sizeBytes,
    Instant uploadedAt
) { }
