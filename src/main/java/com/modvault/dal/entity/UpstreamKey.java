package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An encrypted upstream provider key (single enabled key per upstream, A-10).
 */
@Data
public class UpstreamKey {

    private UUID id;

    private UUID upstreamId;

    /** {@code v1:} prefixed AES-256-GCM ciphertext. */
    private String keyCiphertext;

    /** Masked display form, e.g. {@code sk-...9f2a}. */
    private String keyMasked;

    private boolean enabled;

    private OffsetDateTime lastUsedAt;

    /** {@code active} or {@code unavailable} (e.g. tampered ciphertext). */
    private String status;

    private OffsetDateTime createdAt;
}
