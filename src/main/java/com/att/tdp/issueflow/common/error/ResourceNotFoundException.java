package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 404. Thrown when a required entity is missing or soft-deleted. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String type, Object id) {
        return new ResourceNotFoundException(type + " with id " + id + " not found");
    }
}
