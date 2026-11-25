package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token usage in the Anthropic message shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicUsage {

    @JsonProperty("input_tokens")
    private int inputTokens;

    @JsonProperty("output_tokens")
    private int outputTokens;
}
