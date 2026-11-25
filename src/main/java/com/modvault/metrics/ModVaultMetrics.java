package com.modvault.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prometheus metrics. Tags are bounded (status, resolved model, upstream status code);
 * request-id and key material are never used as tags (FR-27).
 */
@Component
public class ModVaultMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger streamActive = new AtomicInteger(0);

    public ModVaultMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("modvault.stream.active", streamActive, AtomicInteger::get)
                .description("Active streaming SSE connections")
                .register(registry);
    }

    public void recordRequest(String status, String model) {
        registry.counter("modvault.requests.total", "status", status, "model", model).increment();
    }

    public void recordLatency(String model, long millis) {
        registry.timer("modvault.request.duration", "model", model).record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordUpstreamStatus(int statusCode) {
        registry.counter("modvault.upstream.status.total", "code", String.valueOf(statusCode)).increment();
    }

    public void incStreamActive() {
        streamActive.incrementAndGet();
    }

    public void decStreamActive() {
        streamActive.decrementAndGet();
    }
}
