package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A gateway API key (only SHA-256 hash + 8-char prefix stored, A-02).
 */
@Data
public class ApiKey {

    private UUID id;

    private String name;

    private String keyHash;

    private String keyPrefix;

    private boolean enabled;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime createdAt;
}
