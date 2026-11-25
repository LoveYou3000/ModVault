package com.modvault.web;

import com.modvault.common.ApiException;
import com.modvault.service.MessageService;
import com.modvault.translate.model.AnthropicContentBlock;
import com.modvault.translate.model.AnthropicMessageResponse;
import com.modvault.translate.model.AnthropicUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessagesControllerTest {

    private MessageService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(MessageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MessagesController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAnthropicMessageShape() throws Exception {
        AnthropicMessageResponse resp = AnthropicMessageResponse.builder()
                .id("msg_abc")
                .type("message")
                .role("assistant")
                .model("claude-3-5-sonnet")
                .content(Collections.singletonList(AnthropicContentBlock.builder().type("text").text("Hello").build()))
                .stopReason("end_turn")
                .stopSequence(null)
                .usage(AnthropicUsage.builder().inputTokens(10).outputTokens(5).build())
                .build();
        when(service.messages(any(), isNull(), isNull())).thenReturn(resp);

        mvc.perform(post("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"max_tokens\":100,"
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("message"))
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content[0].type").value("text"))
                .andExpect(jsonPath("$.usage.input_tokens").value(10));
    }

    @Test
    void notFoundProducesAnthropicErrorBody() throws Exception {
        when(service.messages(any(), isNull(), isNull()))
                .thenThrow(ApiException.notFound("model not found: x"));

        mvc.perform(post("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"x\",\"max_tokens\":100,"
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("not_found_error"));
    }

    @Test
    void malformedBodyProducesInvalidRequest() throws Exception {
        mvc.perform(post("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    @Test
    void overloadedErrorRendersHttp529() throws Exception {
        when(service.messages(any(), isNull(), isNull()))
                .thenThrow(ApiException.overloaded("upstream unreachable or timed out"));

        mvc.perform(post("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"x\",\"max_tokens\":100,"
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().is(529))
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("overloaded_error"));
    }

    @Test
    void messagesAsStringProducesInvalidRequest() throws Exception {
        mvc.perform(post("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"x\",\"max_tokens\":100,\"messages\":\"not-an-array\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    @Test
    void countTokensReturnsPositiveInteger() throws Exception {
        when(service.countTokens(any())).thenReturn(7);
        mvc.perform(post("/v1/messages/count_tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input_tokens").value(7));
    }
}
