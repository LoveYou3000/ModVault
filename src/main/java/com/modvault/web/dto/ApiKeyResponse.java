package com.modvault.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    private String id;

    private String name;

    @JsonProperty("key_prefix")
    private String keyPrefix;

    private boolean enabled;

    @JsonProperty("expires_at")
    private String expiresAt;

    @JsonProperty("last_used_at")
    private String lastUsedAt;

    @JsonProperty("created_at")
    private String createdAt;
}
