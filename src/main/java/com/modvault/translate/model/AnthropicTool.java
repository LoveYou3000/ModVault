package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Anthropic tool definition: {@code {name, description, input_schema}}.
 */
@Getter
@Setter
@NoArgsConstructor
public class AnthropicTool {

    private String name;

    private String description;

    @JsonProperty("input_schema")
    private JsonNode inputSchema;
}
