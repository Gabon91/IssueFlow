package com.att.tdp.issueflow.auditlog;

/**
 * Origin of an audit event. {@link #SYSTEM} is used for background jobs
 * such as auto-assignment and auto-escalation.
 */
public enum AuditActor {
    USER,
    SYSTEM
}
