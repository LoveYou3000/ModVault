package com.modvault.translate.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry of the Anthropic {@code messages[]} array. {@code content} is either a
 * JSON string or an array of content blocks; both are parsed downstream.
 */
@Getter
@Setter
@NoArgsConstructor
public class AnthropicMessage {

    private String role;

    private JsonNode content;
}
