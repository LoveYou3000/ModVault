package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * An OpenAI-compatible upstream configuration.
 */
@Data
public class Upstream {

    private String id;

    private String name;

    private String baseUrl;

    private boolean enabled;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    /** JSON object of extra headers, stored as text. */
    private String extraHeaders;

    private String maxTokensField;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
