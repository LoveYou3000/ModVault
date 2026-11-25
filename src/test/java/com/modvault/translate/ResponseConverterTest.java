package com.modvault.translate;

import com.modvault.translate.model.AnthropicMessageResponse;
import com.modvault.translate.model.OpenAiChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseConverterTest {

    private final ResponseConverter converter = new ResponseConverter();

    private OpenAiChatResponse parse(String json) throws Exception {
        return Json.mapper().readValue(json, OpenAiChatResponse.class);
    }

    @Test
    void textAndStopReason() throws Exception {
        OpenAiChatResponse in = parse("{\"id\":\"c1\",\"model\":\"gpt-4o\","
                + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");
        AnthropicMessageResponse out = converter.convert(in, "claude-3-5-sonnet", new ArrayList<>());
        assertEquals("message", out.getType());
        assertEquals("assistant", out.getRole());
        assertEquals("claude-3-5-sonnet", out.getModel());
        assertEquals(1, out.getContent().size());
        assertEquals("text", out.getContent().get(0).getType());
        assertEquals("Hello", out.getContent().get(0).getText());
        assertEquals("end_turn", out.getStopReason());
        assertNull(out.getStopSequence());
        assertEquals(10, out.getUsage().getInputTokens());
        assertEquals(5, out.getUsage().getOutputTokens());
    }

    @Test
    void finishReasonLengthMapsToMaxTokens() throws Exception {
        OpenAiChatResponse in = parse("{\"choices\":[{\"finish_reason\":\"length\","
                + "\"message\":{\"content\":\"x\"}}]}");
        assertEquals("max_tokens", converter.convert(in, "m", new ArrayList<>()).getStopReason());
    }

    @Test
    void finishReasonToolCallsMapsToToolUse() throws Exception {
        OpenAiChatResponse in = parse("{\"choices\":[{\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"tool_calls\":[{\"id\":\"t1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"f\",\"arguments\":\"{\\\"x\\\":1}\"}}]}}]}");
        AnthropicMessageResponse out = converter.convert(in, "m", new ArrayList<>());
        assertEquals("tool_use", out.getStopReason());
        assertEquals(1, out.getContent().size());
        assertEquals("tool_use", out.getContent().get(0).getType());
        assertEquals("t1", out.getContent().get(0).getId());
        assertEquals("f", out.getContent().get(0).getName());
        assertEquals(1, out.getContent().get(0).getInput().get("x").asInt());
    }

    @Test
    void invalidArgumentsBecomesEmptyObjectWithWarning() throws Exception {
        OpenAiChatResponse in = parse("{\"choices\":[{\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"tool_calls\":[{\"id\":\"t1\",\"function\":{\"name\":\"f\",\"arguments\":\"not json\"}}]}}]}");
        ArrayList<String> warnings = new ArrayList<>();
        AnthropicMessageResponse out = converter.convert(in, "m", warnings);
        assertTrue(out.getContent().get(0).getInput().isObject());
        assertEquals(0, out.getContent().get(0).getInput().size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("parse failed")));
    }

    @Test
    void emptyContentYieldsEmptyBlocks() throws Exception {
        OpenAiChatResponse in = parse("{\"choices\":[{\"finish_reason\":\"stop\","
                + "\"message\":{\"content\":\"\"}}]}");
        assertEquals(0, converter.convert(in, "m", new ArrayList<>()).getContent().size());
    }
}
