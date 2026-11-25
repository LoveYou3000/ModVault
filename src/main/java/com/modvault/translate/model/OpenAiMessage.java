package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One OpenAI chat message. {@code tool_calls} on assistant messages,
 * {@code tool_call_id} on role=tool messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiMessage {

    private String role;

    private String content;

    @JsonProperty("tool_calls")
    private List<OpenAiToolCall> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    private String name;
}
