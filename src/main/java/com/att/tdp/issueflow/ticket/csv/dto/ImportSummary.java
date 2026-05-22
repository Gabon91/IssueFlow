package com.att.tdp.issueflow.ticket.csv.dto;

import java.util.List;

/** Response of {@code POST /tickets/import}. Mirrors {@code ImportSummary} in {@code openapi.yaml}. */
public record ImportSummary(int created, int failed, List<ImportRowError> errors) {}
