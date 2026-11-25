package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Maps a client-facing Anthropic model name to an upstream model name.
 */
@Data
public class ModelMapping {

    private String id;

    private String anthropicModel;

    private String upstreamId;

    private String upstreamModel;

    private boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
