package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.modvault.common.ApiException;
import com.modvault.common.Ids;
import com.modvault.translate.model.AnthropicContentBlock;
import com.modvault.translate.model.AnthropicMessageResponse;
import com.modvault.translate.model.AnthropicUsage;
import com.modvault.translate.model.OpenAiChatResponse;
import com.modvault.translate.model.OpenAiToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Chat Completions response -&gt; Anthropic Message (pure, no IO).
 */
public class ResponseConverter {

    public AnthropicMessageResponse convert(OpenAiChatResponse in, String clientModel, List<String> warnings) {
        OpenAiChatResponse.OpenAiChoice choice = null;
        if (in.getChoices() != null && !in.getChoices().isEmpty()) {
            choice = in.getChoices().get(0);
        }
        if (choice == null) {
            throw ApiException.apiError("upstream response has no choices");
        }

        List<AnthropicContentBlock> blocks = new ArrayList<>();
        OpenAiChatResponse.OpenAiResponseMessage message = choice.getMessage();
        if (message != null) {
            String content = message.getContent();
            if (content != null && !content.isEmpty()) {
                blocks.add(AnthropicContentBlock.builder().type("text").text(content).build());
            }
            if (message.getToolCalls() != null) {
                for (OpenAiToolCall tc : message.getToolCalls()) {
                    String name = tc.getFunction() != null ? tc.getFunction().getName() : null;
                    String arguments = tc.getFunction() != null ? tc.getFunction().getArguments() : null;
                    blocks.add(AnthropicContentBlock.builder()
                            .type("tool_use")
                            .id(tc.getId())
                            .name(name)
                            .input(parseArguments(arguments, warnings))
                            .build());
                }
            }
        }

        int inputTokens = in.getUsage() != null ? in.getUsage().getPromptTokens() : 0;
        int outputTokens = in.getUsage() != null ? in.getUsage().getCompletionTokens() : 0;

        return AnthropicMessageResponse.builder()
                .id(Ids.messageId())
                .type("message")
                .role("assistant")
                .model(clientModel)
                .content(blocks)
                .stopReason(mapFinishReason(choice.getFinishReason(), warnings))
                .stopSequence(null)
                .usage(AnthropicUsage.builder().inputTokens(inputTokens).outputTokens(outputTokens).build())
                .build();
    }

    private JsonNode parseArguments(String arguments, List<String> warnings) {
        if (arguments == null || arguments.isEmpty()) {
            return Json.mapper().getNodeFactory().objectNode();
        }
        try {
            return Json.mapper().readTree(arguments);
        } catch (Exception e) {
            warnings.add("tool_use input parse failed; using {}");
            return Json.mapper().getNodeFactory().objectNode();
        }
    }

    private String mapFinishReason(String reason, List<String> warnings) {
        if (reason == null) {
            warnings.add("finish_reason null; mapped to end_turn");
            return "end_turn";
        }
        if ("stop".equals(reason)) {
            return "end_turn";
        }
        if ("length".equals(reason)) {
            return "max_tokens";
        }
        if ("tool_calls".equals(reason)) {
            return "tool_use";
        }
        if ("content_filter".equals(reason)) {
            return "refusal";
        }
        warnings.add("unknown finish_reason: " + reason);
        return "end_turn";
    }
}
