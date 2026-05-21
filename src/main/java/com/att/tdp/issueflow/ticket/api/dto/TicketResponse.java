package com.att.tdp.issueflow.ticket.api.dto;

import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.ticket.domain.TicketType;
import java.time.Instant;

/** Response body for ticket-returning endpoints. */
public record TicketResponse(
    Long id,
    String title,
    String description,
    TicketStatus status,
    TicketPriority priority,
    TicketType type,
    Long projectId,
    Long assigneeId,
    Instant dueDate,
    boolean isOverdue
) {}
