package com.att.tdp.issueflow.user;

/**
 * Coarse-grained authorization role assigned to a {@link User}.
 *
 * <ul>
 *   <li>{@link #ADMIN} – full access; can manage users, projects and soft-deleted resources.</li>
 *   <li>{@link #DEVELOPER} – default role; can author tickets, comments and attachments.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    DEVELOPER
}
