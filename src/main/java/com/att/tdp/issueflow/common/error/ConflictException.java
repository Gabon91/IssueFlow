package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 409. Generic semantic conflict (e.g. duplicate username/email). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
