package com.att.tdp.issueflow.common.error;

/** Mapped to HTTP 415. Raised when an uploaded file's MIME type is not in the allow-list. */
public class UnsupportedMimeTypeException extends RuntimeException {
    public UnsupportedMimeTypeException(String message) {
        super(message);
    }
}
