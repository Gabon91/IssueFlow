package com.att.tdp.issueflow.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link TicketDependency}: the pair (ticket, blocker).
 * Read as "{@code ticketId} is blocked by {@code blockedByTicketId}".
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketDependencyId implements Serializable {

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "blocked_by_ticket_id")
    private Long blockedByTicketId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TicketDependencyId other)) return false;
        return Objects.equals(ticketId, other.ticketId)
            && Objects.equals(blockedByTicketId, other.blockedByTicketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId, blockedByTicketId);
    }
}
