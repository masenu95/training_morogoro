package com.training.orderservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLoggingConfig.class);

    public CircuitBreakerLoggingConfig(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::logTransitions);
        registry.getEventPublisher().onEntryAdded(event -> logTransitions(event.getAddedEntry()));
    }

    private void logTransitions(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(e ->
                log.warn(">>> CIRCUIT BREAKER [{}]: {}", e.getCircuitBreakerName(), e.getStateTransition()));
    }
}
