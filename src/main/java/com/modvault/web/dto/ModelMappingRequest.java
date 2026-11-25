package com.modvault.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class ModelMappingRequest {

    @JsonProperty("anthropic_model")
    private String anthropicModel;

    @JsonProperty("upstream_id")
    private UUID upstreamId;

    @JsonProperty("upstream_model")
    private String upstreamModel;

    private Boolean enabled;
}
