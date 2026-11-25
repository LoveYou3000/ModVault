package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic response content block (text or tool_use). Null fields are omitted so a
 * text block serializes as {@code {type:"text",text:"..."}} and a tool block as
 * {@code {type:"tool_use",id,name,input}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicContentBlock {

    private String type;

    private String text;

    private String id;

    private String name;

    private JsonNode input;
}
