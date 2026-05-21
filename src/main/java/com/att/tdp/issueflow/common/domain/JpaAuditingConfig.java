package com.att.tdp.issueflow.common.domain;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing in isolation from the main application class so
 * {@code @WebMvcTest} slices (which exclude {@code @Configuration} beans by default)
 * don't drag in the {@code jpaAuditingHandler} bean and fail to start without JPA.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
