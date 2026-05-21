package com.att.tdp.issueflow.ticket.api;

import com.att.tdp.issueflow.ticket.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.ticket.api.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for the Ticket aggregate. */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<TicketResponse> listByProject(@RequestParam Long projectId) {
        return ticketService.findByProject(projectId);
    }

    @PostMapping
    public TicketResponse create(@Valid @RequestBody TicketCreateRequest body) {
        return ticketService.create(body);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable Long ticketId) {
        return ticketService.findById(ticketId);
    }

    @PatchMapping("/{ticketId}")
    public TicketResponse update(@PathVariable Long ticketId,
                                 @Valid @RequestBody TicketUpdateRequest body) {
        return ticketService.update(ticketId, body);
    }

    @DeleteMapping("/{ticketId}")
    public void delete(@PathVariable Long ticketId) {
        ticketService.softDelete(ticketId);
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TicketResponse> listDeleted(@RequestParam Long projectId) {
        return ticketService.findDeleted(projectId);
    }

    @PostMapping("/{ticketId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public void restore(@PathVariable Long ticketId) {
        ticketService.restore(ticketId);
    }
}
