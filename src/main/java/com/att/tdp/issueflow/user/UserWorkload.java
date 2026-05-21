package com.att.tdp.issueflow.user;

/**
 * Interface projection returned by {@link UserRepository#findDeveloperWorkloads()}.
 * Each row pairs a developer with their current count of non-{@code DONE} tickets.
 */
public interface UserWorkload {

    Long getUserId();

    String getUsername();

    String getFullName();

    long getOpenTicketCount();
}
