package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Non-streaming Anthropic Messages response (FR-01).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicMessageResponse {

    private String id;

    private String type;

    private String role;

    private String model;

    private List<AnthropicContentBlock> content;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    private AnthropicUsage usage;
}
