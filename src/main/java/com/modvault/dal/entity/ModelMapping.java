package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps a client-facing Anthropic model name to an upstream model name.
 */
@Data
public class ModelMapping {

    private UUID id;

    private String anthropicModel;

    private UUID upstreamId;

    private String upstreamModel;

    private boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
