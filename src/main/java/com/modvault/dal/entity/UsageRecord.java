package com.modvault.dal.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * One usage record per request (metadata only; no prompt/completion content).
 */
@Data
public class UsageRecord {

    private String id;

    private String requestId;

    private String apiKeyId;

    private String anthropicModel;

    private String upstreamId;

    private String upstreamModel;

    /** success | failed | rejected */
    private String status;

    private Integer inputTokens;

    private Integer outputTokens;

    private boolean stream;

    private Long latencyMs;

    private Long firstTokenMs;

    private String errorType;

    private OffsetDateTime createdAt;
}
