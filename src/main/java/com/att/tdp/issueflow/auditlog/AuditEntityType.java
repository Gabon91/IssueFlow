package com.att.tdp.issueflow.auditlog;

/**
 * Domain aggregate that the audited action targeted.
 */
public enum AuditEntityType {
    USER,
    PROJECT,
    TICKET,
    COMMENT,
    DEPENDENCY,
    ATTACHMENT
}
