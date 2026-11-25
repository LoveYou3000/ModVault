package com.modvault.translate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI Chat Completions request. Null fields are omitted when serialized upstream.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

    private String model;

    private List<OpenAiMessage> messages;

    private List<OpenAiTool> tools;

    /** String ("auto"/"none"/"required") or object form. */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    private List<String> stop;

    private Boolean stream;

    @JsonProperty("stream_options")
    private OpenAiStreamOptions streamOptions;

    private String user;
}
