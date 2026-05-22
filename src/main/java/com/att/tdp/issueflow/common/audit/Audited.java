package com.att.tdp.issueflow.common.audit;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful execution should produce an {@code AuditLog} row.
 * Intercepted by {@link AuditAspect}.
 *
 * <p>The audited entity id is resolved at runtime by, in order:
 * <ol>
 *   <li>The {@code idArg}-th method argument when {@code idArg >= 0} and that argument is a {@link Long};</li>
 *   <li>otherwise, the value of {@code id()} on the method's return value (records work out-of-the-box).</li>
 * </ol>
 * If neither yields a Long, the audit row is silently skipped — the underlying business
 * method's outcome is never affected.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    AuditEntityType entityType();

    /** Index of the argument carrying the entity id, or {@code -1} to use the return value's {@code id()}. */
    int idArg() default -1;
}
