package com.att.tdp.issueflow.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralised {@link Clock} bean. Services that need wall-clock time inject this so tests
 * can swap in a fixed clock for deterministic assertions (notably {@code EscalationService}).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
