package com.att.tdp.issueflow.ticket.domain;

/**
 * Ticket priority. {@link #CRITICAL} is the ceiling used by the auto-escalation scheduler.
 */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
