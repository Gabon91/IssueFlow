package com.att.tdp.issueflow.ticket.domain;

import com.att.tdp.issueflow.common.error.IllegalStateTransitionException;
import java.util.Map;
import java.util.Set;

/**
 * Enforces invariant I1: ticket status transitions are forward-only along
 * {@code TODO → IN_PROGRESS → IN_REVIEW → DONE}. Identity transitions are allowed
 * (no-op). Anything else throws {@link IllegalStateTransitionException}.
 */
public final class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
        TicketStatus.TODO,        Set.of(TicketStatus.IN_PROGRESS),
        TicketStatus.IN_PROGRESS, Set.of(TicketStatus.IN_REVIEW),
        TicketStatus.IN_REVIEW,   Set.of(TicketStatus.DONE),
        TicketStatus.DONE,        Set.of()
    );

    private TicketStateMachine() {}

    /** Returns true if {@code to} is reachable in one step from {@code from} (or is identical). */
    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        if (from == to) return true;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Throws if the transition is not permitted by I1. No-op on identity transitions. */
    public static void assertTransition(TicketStatus from, TicketStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(
                "Illegal status transition: " + from + " -> " + to);
        }
    }
}
