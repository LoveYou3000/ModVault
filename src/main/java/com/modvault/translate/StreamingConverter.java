package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modvault.common.Ids;
import com.modvault.translate.model.OpenAiChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateful OpenAI streaming chunk → Anthropic SSE event converter (pure, no IO).
 * Event sequence follows PRD appendix A.3:
 * message_start → (content_block_start → content_block_delta* → content_block_stop)* →
 * message_delta → message_stop.
 */
public class StreamingConverter {

    private final String clientModel;
    private final int estimatedInputTokens;
    private final List<String> warnings;

    private final String messageId = Ids.messageId();
    private boolean started = false;
    private boolean finalized = false;
    private int nextIndex = 0;
    private int textBlockIndex = -1;
    private final Map<Integer, ToolBlock> toolBlocks = new LinkedHashMap<>();
    private final List<Integer> openIndices = new ArrayList<>();
    private String stopReason = "end_turn";
    private int outputChars = 0;

    public StreamingConverter(String clientModel, int estimatedInputTokens, List<String> warnings) {
        this.clientModel = clientModel;
        this.estimatedInputTokens = estimatedInputTokens;
        this.warnings = warnings;
    }

    public List<AnthropicEvent> onChunk(OpenAiChunk chunk) {
        List<AnthropicEvent> events = new ArrayList<>();
        if (finalized || chunk == null) {
            return events;
        }
        if (!started) {
            events.add(messageStart());
            started = true;
        }
        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
            OpenAiChunk.ChunkChoice choice = chunk.getChoices().get(0);
            OpenAiChunk.ChunkDelta delta = choice.getDelta();
            if (delta != null) {
                handleContent(delta.getContent(), events);
                handleToolCalls(delta.getToolCalls(), events);
            }
            if (choice.getFinishReason() != null) {
                stopReason = FinishReasonMapper.map(choice.getFinishReason(), warnings);
            }
        }
        if (chunk.getUsage() != null) {
            events.addAll(finalizeWithUsage(chunk.getUsage().getCompletionTokens()));
        }
        return events;
    }

    /**
     * Closes open blocks and emits message_delta + message_stop when the upstream ends
     * without a usage chunk (EOF/timeout). Output tokens fall back to a local estimate.
     */
    public List<AnthropicEvent> finalizeWithoutUsage() {
        if (finalized) {
            return new ArrayList<>();
        }
        List<AnthropicEvent> events = new ArrayList<>();
        closeOpenBlocks(events);
        events.add(messageDelta(estimateOutputTokens()));
        events.add(messageStop());
        finalized = true;
        return events;
    }

    /** Closes open blocks only (for the mid-stream error path, before the error event). */
    public List<AnthropicEvent> abort() {
        List<AnthropicEvent> events = new ArrayList<>();
        if (finalized) {
            return events;
        }
        closeOpenBlocks(events);
        return events;
    }

    public boolean isFinalized() {
        return finalized;
    }

    /** Rough local output-token estimate from accumulated text/tool deltas (A-16-style). */
    public int estimateOutputTokens() {
        return Math.max(1, (int) Math.ceil(outputChars / 4.0));
    }

    private List<AnthropicEvent> finalizeWithUsage(int outputTokens) {
        List<AnthropicEvent> events = new ArrayList<>();
        closeOpenBlocks(events);
        events.add(messageDelta(outputTokens));
        events.add(messageStop());
        finalized = true;
        return events;
    }

    private void closeOpenBlocks(List<AnthropicEvent> events) {
        for (Integer index : openIndices) {
            events.add(blockStop(index));
        }
        openIndices.clear();
    }

    private void handleContent(String content, List<AnthropicEvent> events) {
        if (content == null || content.isEmpty()) {
            return;
        }
        outputChars += content.length();
        if (textBlockIndex < 0) {
            textBlockIndex = nextIndex++;
            openIndices.add(textBlockIndex);
            events.add(textBlockStart(textBlockIndex));
        }
        events.add(textDelta(textBlockIndex, content));
    }

    private void handleToolCalls(List<OpenAiChunk.ChunkToolCall> toolCalls, List<AnthropicEvent> events) {
        if (toolCalls == null) {
            return;
        }
        for (OpenAiChunk.ChunkToolCall tc : toolCalls) {
            if (tc.getId() != null) {
                int index = nextIndex++;
                String name = tc.getFunction() != null ? tc.getFunction().getName() : null;
                ToolBlock block = new ToolBlock(index, tc.getId(), name);
                toolBlocks.put(tc.getIndex(), block);
                openIndices.add(index);
                events.add(toolUseBlockStart(index, block));
                String args = tc.getFunction() != null ? tc.getFunction().getArguments() : null;
                if (args != null && !args.isEmpty()) {
                    block.args.append(args);
                    outputChars += args.length();
                    events.add(inputJsonDelta(index, args));
                }
            } else {
                ToolBlock block = toolBlocks.get(tc.getIndex());
                if (block == null) {
                    continue;
                }
                String args = tc.getFunction() != null ? tc.getFunction().getArguments() : null;
                if (args != null && !args.isEmpty()) {
                    block.args.append(args);
                    outputChars += args.length();
                    events.add(inputJsonDelta(block.index, args));
                }
            }
        }
    }

    private AnthropicEvent messageStart() {
        ObjectNode usage = Json.mapper().createObjectNode();
        usage.put("input_tokens", estimatedInputTokens);
        usage.put("output_tokens", 1);

        ObjectNode message = Json.mapper().createObjectNode();
        message.put("id", messageId);
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", clientModel);
        message.set("content", Json.mapper().createArrayNode());
        message.putNull("stop_reason");
        message.putNull("stop_sequence");
        message.set("usage", usage);

        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "message_start");
        data.set("message", message);
        return new AnthropicEvent("message_start", data);
    }

    private AnthropicEvent textBlockStart(int index) {
        ObjectNode block = Json.mapper().createObjectNode();
        block.put("type", "text");
        block.put("text", "");
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "content_block_start");
        data.put("index", index);
        data.set("content_block", block);
        return new AnthropicEvent("content_block_start", data);
    }

    private AnthropicEvent textDelta(int index, String text) {
        ObjectNode delta = Json.mapper().createObjectNode();
        delta.put("type", "text_delta");
        delta.put("text", text);
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "content_block_delta");
        data.put("index", index);
        data.set("delta", delta);
        return new AnthropicEvent("content_block_delta", data);
    }

    private AnthropicEvent toolUseBlockStart(int index, ToolBlock block) {
        ObjectNode content = Json.mapper().createObjectNode();
        content.put("type", "tool_use");
        content.put("id", block.id);
        content.put("name", block.name);
        content.set("input", Json.mapper().createObjectNode());
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "content_block_start");
        data.put("index", index);
        data.set("content_block", content);
        return new AnthropicEvent("content_block_start", data);
    }

    private AnthropicEvent inputJsonDelta(int index, String partial) {
        ObjectNode delta = Json.mapper().createObjectNode();
        delta.put("type", "input_json_delta");
        delta.put("partial_json", partial);
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "content_block_delta");
        data.put("index", index);
        data.set("delta", delta);
        return new AnthropicEvent("content_block_delta", data);
    }

    private AnthropicEvent blockStop(int index) {
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "content_block_stop");
        data.put("index", index);
        return new AnthropicEvent("content_block_stop", data);
    }

    private AnthropicEvent messageDelta(int outputTokens) {
        ObjectNode delta = Json.mapper().createObjectNode();
        delta.put("stop_reason", stopReason);
        delta.putNull("stop_sequence");
        ObjectNode usage = Json.mapper().createObjectNode();
        usage.put("output_tokens", outputTokens);
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "message_delta");
        data.set("delta", delta);
        data.set("usage", usage);
        return new AnthropicEvent("message_delta", data);
    }

    private AnthropicEvent messageStop() {
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "message_stop");
        return new AnthropicEvent("message_stop", data);
    }

    /** Builds an Anthropic error event (FR-14). */
    public static AnthropicEvent errorEvent(String type, String message) {
        ObjectNode error = Json.mapper().createObjectNode();
        error.put("type", type);
        error.put("message", message);
        ObjectNode data = Json.mapper().createObjectNode();
        data.put("type", "error");
        data.set("error", error);
        return new AnthropicEvent("error", data);
    }

    private static final class ToolBlock {
        final int index;
        final String id;
        final String name;
        final StringBuilder args = new StringBuilder();

        ToolBlock(int index, String id, String name) {
            this.index = index;
            this.id = id;
            this.name = name;
        }
    }
}
