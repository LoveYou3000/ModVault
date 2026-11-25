package com.modvault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ModVault runtime configuration, injected from environment variables
 * (see {@code application.yml}).
 */
@ConfigurationProperties(prefix = "modvault")
public class ModVaultProperties {

    /** Base64-encoded 32-byte AES-256 master key (MODVAULT_MASTER_KEY). */
    private String masterKey = "";

    /** Static admin bearer token, >= 32 chars (MODVAULT_ADMIN_TOKEN). */
    private String adminToken = "";

    /** Append {@code /v1} to a root upstream base_url. */
    private boolean autoAppendV1 = true;

    private int defaultConnectTimeoutMs = 10_000;
    private int defaultReadTimeoutMs = 120_000;

    private int maxRequestBodyBytes = 2 * 1024 * 1024;

    private int retentionDays = 90;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public boolean isAutoAppendV1() {
        return autoAppendV1;
    }

    public void setAutoAppendV1(boolean autoAppendV1) {
        this.autoAppendV1 = autoAppendV1;
    }

    public int getDefaultConnectTimeoutMs() {
        return defaultConnectTimeoutMs;
    }

    public void setDefaultConnectTimeoutMs(int defaultConnectTimeoutMs) {
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
    }

    public int getDefaultReadTimeoutMs() {
        return defaultReadTimeoutMs;
    }

    public void setDefaultReadTimeoutMs(int defaultReadTimeoutMs) {
        this.defaultReadTimeoutMs = defaultReadTimeoutMs;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
