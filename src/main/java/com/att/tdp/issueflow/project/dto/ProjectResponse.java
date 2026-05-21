package com.att.tdp.issueflow.project.dto;

/** Response body for project-returning endpoints. */
public record ProjectResponse(
    Long id,
    String name,
    String description,
    Long ownerId
) {}
