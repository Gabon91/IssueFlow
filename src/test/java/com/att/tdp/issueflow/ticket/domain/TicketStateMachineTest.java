package com.att.tdp.issueflow.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.att.tdp.issueflow.common.error.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

/** Pinpoints invariant I1: forward-only transitions along TODO→IN_PROGRESS→IN_REVIEW→DONE. */
class TicketStateMachineTest {

    @Test
    void allowsForwardTransitionsAlongTheHappyPath() {
        assertThat(TicketStateMachine.canTransition(TicketStatus.TODO, TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW)).isTrue();
        assertThat(TicketStateMachine.canTransition(TicketStatus.IN_REVIEW, TicketStatus.DONE)).isTrue();
    }

    @Test
    void allowsIdentityTransitionAsNoop() {
        for (TicketStatus s : TicketStatus.values()) {
            assertThat(TicketStateMachine.canTransition(s, s)).as("identity %s->%s", s, s).isTrue();
        }
    }

    @Test
    void rejectsSkippingStates() {
        assertThat(TicketStateMachine.canTransition(TicketStatus.TODO, TicketStatus.IN_REVIEW)).isFalse();
        assertThat(TicketStateMachine.canTransition(TicketStatus.TODO, TicketStatus.DONE)).isFalse();
        assertThat(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.DONE)).isFalse();
    }

    @Test
    void rejectsBackwardTransitions() {
        assertThat(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.TODO)).isFalse();
        assertThat(TicketStateMachine.canTransition(TicketStatus.IN_REVIEW, TicketStatus.IN_PROGRESS)).isFalse();
        assertThat(TicketStateMachine.canTransition(TicketStatus.DONE, TicketStatus.IN_REVIEW)).isFalse();
        assertThat(TicketStateMachine.canTransition(TicketStatus.DONE, TicketStatus.TODO)).isFalse();
    }

    @Test
    void assertTransitionThrowsOnIllegalMoveWithDescriptiveMessage() {
        assertThatThrownBy(() -> TicketStateMachine.assertTransition(TicketStatus.TODO, TicketStatus.DONE))
            .isInstanceOf(IllegalStateTransitionException.class)
            .hasMessageContaining("TODO")
            .hasMessageContaining("DONE");
    }

    @Test
    void assertTransitionIsSilentOnLegalMove() {
        TicketStateMachine.assertTransition(TicketStatus.TODO, TicketStatus.IN_PROGRESS);
        TicketStateMachine.assertTransition(TicketStatus.IN_REVIEW, TicketStatus.IN_REVIEW);
    }
}
