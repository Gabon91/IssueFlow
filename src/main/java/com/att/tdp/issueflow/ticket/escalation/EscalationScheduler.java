package com.att.tdp.issueflow.ticket.escalation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link EscalationService#escalate(int)} on a fixed cadence. Interval and batch size
 * are configurable via {@code issueflow.escalation.*}; defaults are 5-minute interval with a
 * 1-minute startup delay so the first scan does not race with Flyway/JPA initialization.
 * Exceptions from a single run are caught and logged so the scheduler stays alive.
 */
@Component
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    private final EscalationService escalationService;
    private final int batchSize;

    public EscalationScheduler(EscalationService escalationService,
                               @Value("${issueflow.escalation.batch-size:100}") int batchSize) {
        this.escalationService = escalationService;
        this.batchSize = batchSize;
    }

    @Scheduled(
        fixedDelayString = "${issueflow.escalation.interval:PT5M}",
        initialDelayString = "${issueflow.escalation.initial-delay:PT1M}")
    public void run() {
        try {
            escalationService.escalate(batchSize);
        } catch (Exception ex) {
            log.warn("EscalationScheduler tick failed: {}", ex.toString());
        }
    }
}
