package com.att.tdp.issueflow.auditlog;

/**
 * Action recorded by the audit log. Mutating actions are written by {@code AuditAspect};
 * {@link #AUTO_ASSIGN} and {@link #AUTO_ESCALATE} are written manually by background services.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE,
    AUTO_ASSIGN,
    AUTO_ESCALATE,
    LOGIN,
    LOGOUT
}
