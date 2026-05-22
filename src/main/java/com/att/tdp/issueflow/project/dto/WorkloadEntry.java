package com.att.tdp.issueflow.project.dto;

/**
 * One row of {@code GET /projects/{projectId}/workload}: a developer and their count of open
 * (non-{@code DONE}) tickets in the project. Shape mirrors the {@code WorkloadEntry} schema in
 * {@code openapi.yaml}.
 */
public record WorkloadEntry(
    Long userId,
    String username,
    long openTicketCount
) {}
