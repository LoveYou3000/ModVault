package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * A gateway API key (only SHA-256 hash + 8-char prefix stored, A-02).
 */
@Data
public class ApiKey {

    private String id;

    private String name;

    private String keyHash;

    private String keyPrefix;

    private boolean enabled;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime createdAt;
}
