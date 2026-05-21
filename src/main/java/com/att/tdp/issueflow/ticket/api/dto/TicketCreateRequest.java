package com.att.tdp.issueflow.ticket.api.dto;

import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.ticket.domain.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request body for {@code POST /tickets}. */
public record TicketCreateRequest(
    @NotBlank @Size(min = 1, max = 200) String title,
    @Size(max = 10000) String description,
    @NotNull TicketStatus status,
    @NotNull TicketPriority priority,
    @NotNull TicketType type,
    @NotNull Long projectId,
    Long assigneeId,
    Instant dueDate
) {}
