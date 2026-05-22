package com.att.tdp.issueflow.ticket.csv.dto;

/** One failed row in an {@link ImportSummary}. {@code row} is 1-based and includes the header. */
public record ImportRowError(long row, String message) {}
