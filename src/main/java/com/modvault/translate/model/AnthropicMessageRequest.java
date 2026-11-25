package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw Anthropic Messages request body. Unknown fields are captured (not rejected)
 * for warning-level logging; only structural errors fail (FR-06).
 */
@Getter
@Setter
@NoArgsConstructor
public class AnthropicMessageRequest {

    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** String or array of text blocks. */
    private JsonNode system;

    private List<AnthropicMessage> messages;

    private List<AnthropicTool> tools;

    @JsonProperty("tool_choice")
    private JsonNode toolChoice;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    private Boolean stream;

    private JsonNode metadata;

    private JsonNode thinking;

    @JsonProperty("mcp_servers")
    private JsonNode mcpServers;

    private JsonNode container;

    @JsonProperty("context_management")
    private JsonNode contextManagement;

    @Getter(AccessLevel.NONE)
    @JsonIgnore
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void captureUnknown(String name, Object value) {
        unknownFields.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getUnknownFields() {
        return unknownFields;
    }
}
