package com.modvault.web;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness detail: master key presence is guaranteed by startup validation in
 * {@code EncryptionService}; this exposes it explicitly for the readiness probe (FR-27).
 */
@Component
public class MasterKeyHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up().withDetail("masterKey", "configured").build();
    }
}
