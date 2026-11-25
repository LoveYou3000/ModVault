package com.modvault.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamResponse {

    private String id;

    private String name;

    @JsonProperty("base_url")
    private String baseUrl;

    private boolean enabled;

    @JsonProperty("connect_timeout_ms")
    private Integer connectTimeoutMs;

    @JsonProperty("read_timeout_ms")
    private Integer readTimeoutMs;

    @JsonProperty("extra_headers")
    private JsonNode extraHeaders;

    @JsonProperty("max_tokens_field")
    private String maxTokensField;

    @JsonProperty("key_masked")
    private String keyMasked;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
