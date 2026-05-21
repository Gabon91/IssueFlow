package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 422. Raised for semantically-rejected operations (self-dependency, cross-project blocker, ...). */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
