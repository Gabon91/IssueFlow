package com.att.tdp.issueflow.common.observability;

import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 4 observability wiring. Two responsibilities: (1) register the
 * {@code issueflow.tickets.overdue} gauge driven by {@link TicketRepository}
 * so the value is computed lazily on scrape rather than recomputed in a loop,
 * and (2) expose the {@link ObservedAspect} bean that activates {@code @Observed}
 * annotations on Spring-managed components.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Gauge overdueTicketsGauge(MeterRegistry registry, TicketRepository tickets) {
        return Gauge.builder("issueflow.tickets.overdue", tickets,
                t -> (double) t.countByIsOverdueTrue())
            .description("Number of tickets currently flagged overdue")
            .register(registry);
    }

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
