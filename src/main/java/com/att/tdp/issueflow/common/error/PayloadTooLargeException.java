package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 413. Raised when an upload exceeds the documented size limit. */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
