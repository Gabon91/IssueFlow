package com.att.tdp.issueflow.comment;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @username} tokens from free-text comment content.
 *
 * <p>Rules:
 * <ul>
 *   <li>A mention is {@code @} followed by 3–50 characters of {@code [A-Za-z0-9_]} —
 *       same alphabet as {@link com.att.tdp.issueflow.user.User#getUsername()}.</li>
 *   <li>Order-preserving deduplication: each username is returned at most once,
 *       in first-occurrence order.</li>
 *   <li>The character immediately preceding {@code @} must be either start-of-input or
 *       whitespace/punctuation (i.e. not a word character), so {@code email@host} is ignored.</li>
 * </ul>
 *
 * <p>The parser deliberately does <em>not</em> verify that the usernames exist in the
 * database — that's the caller's responsibility (the comment service filters against
 * {@code UserRepository}).</p>
 */
public final class MentionParser {

    private static final Pattern PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,50})");

    private MentionParser() {}

    public static Set<String> extract(String content) {
        if (content == null || content.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher m = PATTERN.matcher(content);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
