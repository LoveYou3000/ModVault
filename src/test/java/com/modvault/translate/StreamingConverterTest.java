package com.modvault.translate;

import com.modvault.translate.model.OpenAiChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingConverterTest {

    private List<String> types(List<AnthropicEvent> events) {
        List<String> types = new ArrayList<>();
        for (AnthropicEvent e : events) {
            types.add(e.eventType());
        }
        return types;
    }

    private OpenAiChunk textChunk(String content) {
        OpenAiChunk.ChunkDelta delta = new OpenAiChunk.ChunkDelta();
        delta.setContent(content);
        return choice(delta, null, null);
    }

    private OpenAiChunk roleChunk() {
        OpenAiChunk.ChunkDelta delta = new OpenAiChunk.ChunkDelta();
        delta.setRole("assistant");
        return choice(delta, null, null);
    }

    private OpenAiChunk finishChunk(String finishReason) {
        return choice(new OpenAiChunk.ChunkDelta(), finishReason, null);
    }

    private OpenAiChunk usageChunk(String finishReason, int completionTokens) {
        OpenAiChunk chunk = choice(new OpenAiChunk.ChunkDelta(), finishReason, null);
        OpenAiChunk.OpenAiUsage usage = new OpenAiChunk.OpenAiUsage();
        usage.setCompletionTokens(completionTokens);
        chunk.setUsage(usage);
        return chunk;
    }

    private OpenAiChunk toolChunk(int index, String id, String name, String arguments) {
        OpenAiChunk.ChunkToolCall tc = new OpenAiChunk.ChunkToolCall();
        tc.setIndex(index);
        tc.setId(id);
        OpenAiChunk.ChunkFunction fn = new OpenAiChunk.ChunkFunction();
        fn.setName(name);
        fn.setArguments(arguments);
        tc.setFunction(fn);
        OpenAiChunk.ChunkDelta delta = new OpenAiChunk.ChunkDelta();
        delta.setToolCalls(Collections.singletonList(tc));
        return choice(delta, null, null);
    }

    private OpenAiChunk choice(OpenAiChunk.ChunkDelta delta, String finishReason, OpenAiChunk.OpenAiUsage usage) {
        OpenAiChunk chunk = new OpenAiChunk();
        OpenAiChunk.ChunkChoice choice = new OpenAiChunk.ChunkChoice();
        choice.setDelta(delta);
        choice.setFinishReason(finishReason);
        chunk.setChoices(Collections.singletonList(choice));
        chunk.setUsage(usage);
        return chunk;
    }

    @Test
    void textStreamProducesExpectedSequence() {
        StreamingConverter conv = new StreamingConverter("claude-3-5-sonnet", 10, new ArrayList<>());
        List<AnthropicEvent> events = new ArrayList<>();
        events.addAll(conv.onChunk(roleChunk()));
        events.addAll(conv.onChunk(textChunk("Hel")));
        events.addAll(conv.onChunk(textChunk("lo")));
        events.addAll(conv.onChunk(finishChunk("stop")));
        events.addAll(conv.onChunk(usageChunk("stop", 5)));

        assertEquals(Arrays.asList("message_start", "content_block_start", "content_block_delta",
                "content_block_delta", "content_block_stop", "message_delta", "message_stop"),
                types(events));

        // message_start fields
        AnthropicEvent start = events.get(0);
        assertEquals("claude-3-5-sonnet", start.data().path("message").path("model").asText());
        assertEquals(10, start.data().path("message").path("usage").path("input_tokens").asInt());

        // text deltas
        assertEquals("Hel", events.get(2).data().path("delta").path("text").asText());
        assertEquals("lo", events.get(3).data().path("delta").path("text").asText());

        // message_delta usage
        AnthropicEvent delta = events.get(5);
        assertEquals("message_delta", delta.eventType());
        assertEquals("end_turn", delta.data().path("delta").path("stop_reason").asText());
        assertEquals(5, delta.data().path("usage").path("output_tokens").asInt());

        assertTrue(conv.isFinalized());
    }

    @Test
    void toolCallStreamProducesToolUseBlock() {
        StreamingConverter conv = new StreamingConverter("m", 10, new ArrayList<>());
        List<AnthropicEvent> events = new ArrayList<>();
        events.addAll(conv.onChunk(toolChunk(0, "t1", "get_weather", "")));
        events.addAll(conv.onChunk(toolChunk(0, null, null, "{\"city")));
        events.addAll(conv.onChunk(toolChunk(0, null, null, "\":\"LA\"}")));
        events.addAll(conv.onChunk(finishChunk("tool_calls")));
        events.addAll(conv.onChunk(usageChunk("tool_calls", 20)));

        assertEquals(Arrays.asList("message_start", "content_block_start", "content_block_delta",
                "content_block_delta", "content_block_stop", "message_delta", "message_stop"),
                types(events));

        AnthropicEvent blockStart = events.get(1);
        assertEquals("tool_use", blockStart.data().path("content_block").path("type").asText());
        assertEquals("t1", blockStart.data().path("content_block").path("id").asText());
        assertEquals("get_weather", blockStart.data().path("content_block").path("name").asText());

        assertEquals("{\"city", events.get(2).data().path("delta").path("partial_json").asText());
        assertEquals("\":\"LA\"}", events.get(3).data().path("delta").path("partial_json").asText());

        assertEquals("tool_use", events.get(5).data().path("delta").path("stop_reason").asText());
        assertEquals(20, events.get(5).data().path("usage").path("output_tokens").asInt());
    }

    @Test
    void noUsageChunkFinalizesWithEstimate() {
        StreamingConverter conv = new StreamingConverter("m", 10, new ArrayList<>());
        List<AnthropicEvent> events = new ArrayList<>();
        events.addAll(conv.onChunk(textChunk("Hello world"))); // 11 chars
        events.addAll(conv.onChunk(finishChunk("stop")));
        events.addAll(conv.finalizeWithoutUsage());

        assertEquals(Arrays.asList("message_start", "content_block_start", "content_block_delta",
                "content_block_stop", "message_delta", "message_stop"), types(events));
        // 11 chars -> ceil(11/4)=3
        assertEquals(3, events.get(4).data().path("usage").path("output_tokens").asInt());
        assertTrue(conv.isFinalized());
    }

    @Test
    void abortClosesOpenBlocksWithoutTerminalEvents() {
        StreamingConverter conv = new StreamingConverter("m", 10, new ArrayList<>());
        List<AnthropicEvent> events = new ArrayList<>();
        events.addAll(conv.onChunk(textChunk("Hel")));
        events.addAll(conv.abort());

        assertEquals(Arrays.asList("message_start", "content_block_start", "content_block_delta",
                "content_block_stop"), types(events));
        assertTrue(!conv.isFinalized());
    }

    @Test
    void lengthFinishReasonMapsToMaxTokens() {
        StreamingConverter conv = new StreamingConverter("m", 10, new ArrayList<>());
        List<AnthropicEvent> events = new ArrayList<>();
        events.addAll(conv.onChunk(textChunk("x")));
        events.addAll(conv.onChunk(usageChunk("length", 1)));
        assertEquals("max_tokens", events.get(events.size() - 2).data().path("delta").path("stop_reason").asText());
    }
}
