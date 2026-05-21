package com.att.tdp.issueflow.ticket.domain;

/**
 * Forward-only ticket lifecycle states.
 * Valid transitions: {@code TODO → IN_PROGRESS → IN_REVIEW → DONE}.
 * Backward transitions are rejected by the service layer.
 */
public enum TicketStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE
}
