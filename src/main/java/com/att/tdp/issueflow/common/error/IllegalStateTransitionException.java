package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 422. Raised by the ticket state machine on a backward or invalid transition. */
public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
