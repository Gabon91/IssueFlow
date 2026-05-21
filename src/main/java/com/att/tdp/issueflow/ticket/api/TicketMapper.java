package com.att.tdp.issueflow.ticket.api;

import com.att.tdp.issueflow.ticket.api.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link Ticket} entities and DTOs. */
@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "isOverdue", source = "overdue")
    TicketResponse toResponse(Ticket ticket);
}
