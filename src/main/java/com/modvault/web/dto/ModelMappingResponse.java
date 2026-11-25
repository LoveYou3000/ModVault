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
public class ModelMappingResponse {

    private String id;

    @JsonProperty("anthropic_model")
    private String anthropicModel;

    @JsonProperty("upstream_id")
    private String upstreamId;

    @JsonProperty("upstream_model")
    private String upstreamModel;

    private boolean enabled;

    @JsonProperty("upstream_name")
    private String upstreamName;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
