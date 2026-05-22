package com.att.tdp.issueflow.dependency.dto;

import com.att.tdp.issueflow.ticket.domain.TicketStatus;

/** Response item for {@code GET /tickets/{ticketId}/dependencies}. */
public record DependencyEntry(
    Long id,
    String title,
    TicketStatus status
) {}
