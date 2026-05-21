package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 422. Raised when a ticket cannot move to DONE while open blockers exist. */
public class BlockedByDependencyException extends RuntimeException {
    public BlockedByDependencyException(String message) {
        super(message);
    }
}
