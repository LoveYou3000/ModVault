package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.modvault.common.ApiException;
import com.modvault.translate.model.AnthropicMessage;
import com.modvault.translate.model.AnthropicMessageRequest;
import com.modvault.translate.model.AnthropicTool;
import com.modvault.translate.model.OpenAiChatRequest;
import com.modvault.translate.model.OpenAiFunction;
import com.modvault.translate.model.OpenAiFunctionCall;
import com.modvault.translate.model.OpenAiMessage;
import com.modvault.translate.model.OpenAiStreamOptions;
import com.modvault.translate.model.OpenAiTool;
import com.modvault.translate.model.OpenAiToolCall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Anthropic Messages request -&gt; OpenAI Chat Completions request (pure, no IO).
 * Structural errors throw {@link ApiException}; unsupported fields are collected as warnings.
 */
public class RequestConverter {

    private static final int MAX_STOP_SEQUENCES = 4;

    public RequestConversionResult convert(AnthropicMessageRequest in, String upstreamModel, String maxTokensField) {
        List<String> warnings = new ArrayList<>();

        if (in.getMessages() == null) {
            throw ApiException.badRequest("messages: field required");
        }
        if (in.getMaxTokens() == null) {
            throw ApiException.badRequest("max_tokens: field required");
        }

        OpenAiChatRequest out = new OpenAiChatRequest();
        out.setModel(upstreamModel);

        List<OpenAiMessage> openAiMessages = new ArrayList<>();

        String systemText = ContentText.extractSystemText(in.getSystem());
        if (systemText != null && !systemText.isEmpty()) {
            openAiMessages.add(OpenAiMessage.builder().role("system").content(systemText).build());
        }

        Set<String> toolUseIds = new HashSet<>();
        for (AnthropicMessage msg : in.getMessages()) {
            convertMessage(msg, openAiMessages, warnings, toolUseIds);
        }
        out.setMessages(openAiMessages);

        if ("max_completion_tokens".equals(maxTokensField)) {
            out.setMaxCompletionTokens(in.getMaxTokens());
        } else {
            out.setMaxTokens(in.getMaxTokens());
        }
        out.setTemperature(in.getTemperature());
        out.setTopP(in.getTopP());

        if (in.getStopSequences() != null && !in.getStopSequences().isEmpty()) {
            List<String> stop = in.getStopSequences();
            if (stop.size() > MAX_STOP_SEQUENCES) {
                warnings.add("stop_sequences truncated from " + stop.size() + " to " + MAX_STOP_SEQUENCES);
                stop = new ArrayList<>(stop.subList(0, MAX_STOP_SEQUENCES));
            }
            out.setStop(stop);
        }

        boolean stream = Boolean.TRUE.equals(in.getStream());
        out.setStream(stream);
        if (stream) {
            out.setStreamOptions(OpenAiStreamOptions.builder().includeUsage(true).build());
        }

        String userId = ContentText.extractUserId(in.getMetadata());
        if (userId != null) {
            out.setUser(userId);
        }

        if (in.getTools() != null && !in.getTools().isEmpty()) {
            List<OpenAiTool> tools = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (AnthropicTool t : in.getTools()) {
                if (t.getName() == null || t.getName().isEmpty()) {
                    throw ApiException.badRequest("tools[].name: field required");
                }
                if (!names.add(t.getName())) {
                    throw ApiException.badRequest("tools[]: duplicate tool name '" + t.getName() + "'");
                }
                tools.add(OpenAiTool.builder()
                        .type("function")
                        .function(OpenAiFunction.builder()
                                .name(t.getName())
                                .description(t.getDescription())
                                .parameters(t.getInputSchema())
                                .build())
                        .build());
            }
            out.setTools(tools);
            out.setToolChoice(convertToolChoice(in.getToolChoice(), warnings));
        }

        collectUnsupportedWarnings(in, warnings);

        int estimated = new TokenEstimator().estimateInputTokens(in);
        return new RequestConversionResult(out, warnings, estimated);
    }

    private void convertMessage(AnthropicMessage msg, List<OpenAiMessage> out,
                                List<String> warnings, Set<String> toolUseIds) {
        String role = msg.getRole();
        if (role == null) {
            throw ApiException.badRequest("messages[].role: field required");
        }
        JsonNode content = msg.getContent();

        if ("user".equals(role)) {
            convertUserMessage(content, out, warnings, toolUseIds);
        } else if ("assistant".equals(role)) {
            convertAssistantMessage(content, out, warnings, toolUseIds);
        } else {
            throw ApiException.badRequest("messages[].role: unsupported role '" + role + "'");
        }
    }

    private void convertUserMessage(JsonNode content, List<OpenAiMessage> out,
                                    List<String> warnings, Set<String> toolUseIds) {
        if (content == null || content.isNull()) {
            warnings.add("empty_message_skipped");
            return;
        }
        if (content.isTextual()) {
            if (content.textValue().isEmpty()) {
                warnings.add("empty_message_skipped");
                return;
            }
            out.add(OpenAiMessage.builder().role("user").content(content.textValue()).build());
            return;
        }
        if (!content.isArray()) {
            throw ApiException.badRequest("messages[].content: must be a string or an array of blocks");
        }

        StringBuilder text = new StringBuilder();
        List<OpenAiMessage> toolResults = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type)) {
                text.append(block.path("text").asText(""));
            } else if ("tool_result".equals(type)) {
                flushText(out, "user", text);
                String toolUseId = block.path("tool_use_id").asText("");
                if (!toolUseIds.contains(toolUseId)) {
                    throw ApiException.badRequest("tool_result.tool_use_id: unknown tool_use id '" + toolUseId + "'");
                }
                toolResults.add(OpenAiMessage.builder()
                        .role("tool")
                        .toolCallId(toolUseId)
                        .content(ContentText.extractMessageText(block.path("content")))
                        .build());
            } else if ("image".equals(type) || "document".equals(type) || "audio".equals(type)) {
                throw ApiException.badRequest("unsupported content block type '" + type + "': multimodal input is not supported");
            } else {
                warnings.add("unknown_content_block_type: " + type);
            }
        }
        flushText(out, "user", text);
        out.addAll(toolResults);
    }

    private void convertAssistantMessage(JsonNode content, List<OpenAiMessage> out,
                                         List<String> warnings, Set<String> toolUseIds) {
        if (content == null || content.isNull()) {
            warnings.add("empty_message_skipped");
            return;
        }
        if (content.isTextual()) {
            if (content.textValue().isEmpty()) {
                warnings.add("empty_message_skipped");
                return;
            }
            out.add(OpenAiMessage.builder().role("assistant").content(content.textValue()).build());
            return;
        }
        if (!content.isArray()) {
            throw ApiException.badRequest("messages[].content: must be a string or an array of blocks");
        }

        StringBuilder text = new StringBuilder();
        List<OpenAiToolCall> toolCalls = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type)) {
                text.append(block.path("text").asText(""));
            } else if ("tool_use".equals(type)) {
                String id = block.path("id").asText("");
                String name = block.path("name").asText("");
                JsonNode input = block.path("input");
                if (input.isMissingNode() || input.isNull()) {
                    input = NullNode.getInstance();
                }
                if (!input.isObject()) {
                    throw ApiException.badRequest("tool_use.input: must be a JSON object");
                }
                toolUseIds.add(id);
                toolCalls.add(OpenAiToolCall.builder()
                        .id(id)
                        .type("function")
                        .function(OpenAiFunctionCall.builder().name(name).arguments(input.toString()).build())
                        .build());
            } else if ("tool_result".equals(type)) {
                throw ApiException.badRequest("tool_result block is not allowed in an assistant message");
            } else if ("image".equals(type) || "document".equals(type) || "audio".equals(type)) {
                throw ApiException.badRequest("unsupported content block type '" + type + "': multimodal input is not supported");
            } else {
                warnings.add("unknown_content_block_type: " + type);
            }
        }

        boolean hasText = text.length() > 0;
        boolean hasTools = !toolCalls.isEmpty();
        if (!hasText && !hasTools) {
            warnings.add("empty_message_skipped");
            return;
        }
        OpenAiMessage message = OpenAiMessage.builder().role("assistant").build();
        if (hasText) {
            message.setContent(text.toString());
        }
        if (hasTools) {
            message.setToolCalls(toolCalls);
        }
        out.add(message);
    }

    private void flushText(List<OpenAiMessage> out, String role, StringBuilder text) {
        if (text.length() > 0) {
            out.add(OpenAiMessage.builder().role(role).content(text.toString()).build());
            text.setLength(0);
        }
    }

    private Object convertToolChoice(JsonNode toolChoice, List<String> warnings) {
        if (toolChoice == null || toolChoice.isNull()) {
            return null;
        }
        if (toolChoice.isTextual()) {
            String v = toolChoice.textValue();
            if ("auto".equals(v)) {
                return "auto";
            }
            if ("any".equals(v)) {
                return "required";
            }
            if ("none".equals(v)) {
                return "none";
            }
            warnings.add("unknown_tool_choice: " + v);
            return null;
        }
        if (toolChoice.isObject()) {
            String type = toolChoice.path("type").asText("");
            if ("auto".equals(type)) {
                return "auto";
            }
            if ("any".equals(type)) {
                return "required";
            }
            if ("none".equals(type)) {
                return "none";
            }
            if ("tool".equals(type)) {
                String name = toolChoice.path("name").asText("");
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", name);
                Map<String, Object> choice = new LinkedHashMap<>();
                choice.put("type", "function");
                choice.put("function", function);
                return choice;
            }
            warnings.add("unknown_tool_choice: " + type);
            return null;
        }
        warnings.add("unknown_tool_choice");
        return null;
    }

    private void collectUnsupportedWarnings(AnthropicMessageRequest in, List<String> warnings) {
        if (in.getTopK() != null) {
            warnings.add("dropped_field: top_k");
        }
        if (in.getThinking() != null && !in.getThinking().isNull()) {
            warnings.add("dropped_field: thinking");
        }
        if (in.getMcpServers() != null && !in.getMcpServers().isNull()) {
            warnings.add("dropped_field: mcp_servers");
        }
        if (in.getContainer() != null && !in.getContainer().isNull()) {
            warnings.add("dropped_field: container");
        }
        if (in.getContextManagement() != null && !in.getContextManagement().isNull()) {
            warnings.add("dropped_field: context_management");
        }
        if (in.getMetadata() != null && in.getMetadata().isObject()) {
            Iterator<String> names = in.getMetadata().fieldNames();
            while (names.hasNext()) {
                String key = names.next();
                if (!"user_id".equals(key)) {
                    warnings.add("dropped_field: metadata." + key);
                }
            }
        }
        for (Map.Entry<String, Object> e : in.getUnknownFields().entrySet()) {
            warnings.add("unknown_field_ignored: " + e.getKey());
        }
    }
}
