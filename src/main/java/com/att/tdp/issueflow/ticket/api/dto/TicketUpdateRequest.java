package com.att.tdp.issueflow.ticket.api.dto;

import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Partial update for {@code PATCH /tickets/{ticketId}}. {@code null} means "do not change".
 * Note: tickets in {@code DONE} are immutable and reject any update (invariant I2).
 */
public record TicketUpdateRequest(
    @Size(min = 1, max = 200) String title,
    @Size(max = 10000) String description,
    TicketStatus status,
    TicketPriority priority,
    Long assigneeId,
    Instant dueDate
) {}
