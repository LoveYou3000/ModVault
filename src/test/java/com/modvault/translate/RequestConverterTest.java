package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.modvault.common.ApiException;
import com.modvault.translate.model.AnthropicMessageRequest;
import com.modvault.translate.model.OpenAiChatRequest;
import com.modvault.translate.model.OpenAiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestConverterTest {

    private final RequestConverter converter = new RequestConverter();

    private AnthropicMessageRequest parse(String json) throws Exception {
        return Json.mapper().readValue(json, AnthropicMessageRequest.class);
    }

    @Test
    void systemBlocksJoinedWithDoubleNewline() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"system\":[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"text\",\"text\":\"b\"}],"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_tokens").request();
        assertEquals("system", out.getMessages().get(0).getRole());
        assertEquals("a\n\nb", out.getMessages().get(0).getContent());
    }

    @Test
    void roleOrderSystemUserAssistantUser() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,\"system\":\"sys\","
                + "\"messages\":["
                + "{\"role\":\"user\",\"content\":\"u1\"},"
                + "{\"role\":\"assistant\",\"content\":\"a1\"},"
                + "{\"role\":\"user\",\"content\":\"u2\"}]}");
        List<OpenAiMessage> msgs = converter.convert(req, "gpt-4o", "max_tokens").request().getMessages();
        assertEquals(4, msgs.size());
        assertEquals("system", msgs.get(0).getRole());
        assertEquals("user", msgs.get(1).getRole());
        assertEquals("assistant", msgs.get(2).getRole());
        assertEquals("user", msgs.get(3).getRole());
    }

    @Test
    void emptyAssistantSkipped() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"},{\"role\":\"assistant\",\"content\":\"\"}]}");
        RequestConversionResult result = converter.convert(req, "gpt-4o", "max_tokens");
        assertEquals(1, result.request().getMessages().size());
        assertTrue(result.warnings().contains("empty_message_skipped"));
    }

    @Test
    void samplingAndStopTruncation() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":1024,\"temperature\":0.3,\"top_p\":0.9,"
                + "\"stop_sequences\":[\"a\",\"b\",\"c\",\"d\",\"e\"],"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_tokens").request();
        assertEquals(1024, out.getMaxTokens());
        assertEquals(0.3, out.getTemperature(), 0.0001);
        assertEquals(0.9, out.getTopP(), 0.0001);
        assertEquals(4, out.getStop().size());
    }

    @Test
    void streamAddsIncludeUsage() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,\"stream\":true,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_tokens").request();
        assertTrue(out.getStream());
        assertTrue(out.getStreamOptions().getIncludeUsage());
    }

    @Test
    void maxCompletionTokensFieldName() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_completion_tokens").request();
        assertNull(out.getMaxTokens());
        assertEquals(10, out.getMaxCompletionTokens());
    }

    @Test
    void toolDefinitionConverted() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"tools\":[{\"name\":\"get_weather\",\"description\":\"d\",\"input_schema\":{\"type\":\"object\"}}],"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_tokens").request();
        assertEquals("function", out.getTools().get(0).getType());
        assertEquals("get_weather", out.getTools().get(0).getFunction().getName());
        assertEquals("object", out.getTools().get(0).getFunction().getParameters().get("type").asText());
    }

    @Test
    void assistantToolUseToToolCalls() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"f\",\"input\":{\"x\":1}}]}]}");
        OpenAiMessage msg = converter.convert(req, "gpt-4o", "max_tokens").request().getMessages().get(0);
        assertEquals(1, msg.getToolCalls().size());
        assertEquals("t1", msg.getToolCalls().get(0).getId());
        assertEquals("f", msg.getToolCalls().get(0).getFunction().getName());
        assertEquals("{\"x\":1}", msg.getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    void toolResultToRoleToolMessage() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":["
                + "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"f\",\"input\":{}}]},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\",\"content\":\"result\"}]}]}");
        List<OpenAiMessage> msgs = converter.convert(req, "gpt-4o", "max_tokens").request().getMessages();
        assertEquals(2, msgs.size());
        assertEquals("tool", msgs.get(1).getRole());
        assertEquals("t1", msgs.get(1).getToolCallId());
        assertEquals("result", msgs.get(1).getContent());
    }

    @Test
    void toolChoiceAnyBecomesRequired() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"tools\":[{\"name\":\"f\",\"input_schema\":{}}],"
                + "\"tool_choice\":{\"type\":\"any\"},"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        Object choice = converter.convert(req, "gpt-4o", "max_tokens").request().getToolChoice();
        assertEquals("required", choice);
    }

    @Test
    void unsupportedFieldsWarnedAndDropped() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,\"top_k\":5,"
                + "\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":4096},"
                + "\"foo\":\"bar\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        RequestConversionResult result = converter.convert(req, "gpt-4o", "max_tokens");
        assertTrue(result.warnings().contains("dropped_field: top_k"));
        assertTrue(result.warnings().contains("dropped_field: thinking"));
        assertTrue(result.warnings().contains("unknown_field_ignored: foo"));
    }

    @Test
    void imageBlockRejected() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"image\",\"source\":{}}]}]}");
        ApiException ex = assertThrows(ApiException.class, () -> converter.convert(req, "gpt-4o", "max_tokens"));
        assertEquals("invalid_request_error", ex.errorType().wireValue());
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void missingMaxTokensRejected() throws Exception {
        AnthropicMessageRequest req = parse("{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        ApiException ex = assertThrows(ApiException.class, () -> converter.convert(req, "gpt-4o", "max_tokens"));
        assertTrue(ex.getMessage().contains("max_tokens"));
    }

    @Test
    void missingMessagesRejected() throws Exception {
        AnthropicMessageRequest req = parse("{\"model\":\"m\",\"max_tokens\":10}");
        ApiException ex = assertThrows(ApiException.class, () -> converter.convert(req, "gpt-4o", "max_tokens"));
        assertTrue(ex.getMessage().contains("messages"));
    }

    @Test
    void unknownRoleRejected() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"system\",\"content\":\"x\"}]}");
        assertThrows(ApiException.class, () -> converter.convert(req, "gpt-4o", "max_tokens"));
    }

    @Test
    void metadataUserIdBecomesUser() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,\"metadata\":{\"user_id\":\"u-1\",\"other\":1},"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        OpenAiChatRequest out = converter.convert(req, "gpt-4o", "max_tokens").request();
        assertEquals("u-1", out.getUser());
    }

    @Test
    void estimatedInputTokensPositive() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hello world\"}]}");
        RequestConversionResult result = converter.convert(req, "gpt-4o", "max_tokens");
        assertTrue(result.estimatedInputTokens() > 0);
    }

    @Test
    void nonArrayContentRejected() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"user\",\"content\":{\"type\":\"text\",\"text\":\"x\"}}]}");
        assertThrows(ApiException.class, () -> converter.convert(req, "gpt-4o", "max_tokens"));
    }

    @Test
    void multipleTextBlocksConcatenated() throws Exception {
        AnthropicMessageRequest req = parse("{"
                + "\"model\":\"m\",\"max_tokens\":10,"
                + "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"text\",\"text\":\"b\"}]}]}");
        OpenAiMessage msg = converter.convert(req, "gpt-4o", "max_tokens").request().getMessages().get(0);
        assertEquals("ab", msg.getContent());
    }
}
