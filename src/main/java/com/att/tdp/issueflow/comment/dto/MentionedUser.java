package com.att.tdp.issueflow.comment.dto;

/** Minimal user projection embedded in {@link CommentResponse}. */
public record MentionedUser(
    Long id,
    String username,
    String fullName
) {}
