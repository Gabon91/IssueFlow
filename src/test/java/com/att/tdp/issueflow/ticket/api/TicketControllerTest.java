package com.att.tdp.issueflow.ticket.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.common.error.BlockedByDependencyException;
import com.att.tdp.issueflow.common.error.IllegalStateTransitionException;
import com.att.tdp.issueflow.common.security.JwtAuthenticationFilter;
import com.att.tdp.issueflow.ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test for TicketController: confirms PATCH error responses match the API contract. */
@WebMvcTest(controllers = TicketController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TicketService ticketService;

    @Test
    void illegalStatusTransitionMapsToFourHundredTwentyTwo() throws Exception {
        when(ticketService.update(eq(7L), any()))
            .thenThrow(new IllegalStateTransitionException("Illegal status transition: TODO -> DONE"));

        mvc.perform(patch("/tickets/{id}", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Illegal")));
    }

    @Test
    void blockedByDependencyMapsToFourHundredTwentyTwo() throws Exception {
        when(ticketService.update(eq(7L), any()))
            .thenThrow(new BlockedByDependencyException("Ticket 7 has open blockers and cannot move to DONE"));

        mvc.perform(patch("/tickets/{id}", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("open blockers")));
    }

    @Test
    void titleTooLongIsRejectedAsFourHundredByBeanValidation() throws Exception {
        String tooLong = "a".repeat(201);
        String body = "{\"title\":\"" + tooLong + "\"}";

        mvc.perform(patch("/tickets/{id}", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("title")));
    }
}
