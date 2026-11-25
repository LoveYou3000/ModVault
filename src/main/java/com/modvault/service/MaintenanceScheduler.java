package com.modvault.service;

import com.modvault.config.ModVaultProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention cleanup for usage records (NFR-09). Runs daily; failures are logged and
 * retried next cycle without affecting the data plane.
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final UsageService usageService;
    private final ModVaultProperties props;

    public MaintenanceScheduler(UsageService usageService, ModVaultProperties props) {
        this.usageService = usageService;
        this.props = props;
    }

    @Scheduled(cron = "0 10 3 * * *")
    public void cleanupUsageRecords() {
        try {
            usageService.cleanup(props.getRetentionDays());
            log.info("usage retention cleanup done retention_days={}", props.getRetentionDays());
        } catch (Exception e) {
            log.warn("usage retention cleanup failed: {}", e.getMessage());
        }
    }
}
