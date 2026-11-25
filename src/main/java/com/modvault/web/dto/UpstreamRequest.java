package com.modvault.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpstreamRequest {

    private String name;

    @JsonProperty("base_url")
    private String baseUrl;

    private Boolean enabled;

    @JsonProperty("connect_timeout_ms")
    private Integer connectTimeoutMs;

    @JsonProperty("read_timeout_ms")
    private Integer readTimeoutMs;

    @JsonProperty("extra_headers")
    private JsonNode extraHeaders;

    @JsonProperty("max_tokens_field")
    private String maxTokensField;
}
