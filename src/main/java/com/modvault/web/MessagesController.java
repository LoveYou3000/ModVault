package com.modvault.web;

import com.modvault.dal.entity.ApiKey;
import com.modvault.service.MessageService;
import com.modvault.translate.model.AnthropicMessageRequest;
import com.modvault.translate.model.AnthropicMessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic-compatible data plane: {@code POST /v1/messages} (non-streaming + streaming)
 * and {@code POST /v1/messages/count_tokens}.
 */
@RestController
public class MessagesController {

    private final MessageService messageService;

    public MessagesController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/v1/messages")
    public ResponseEntity<AnthropicMessageResponse> messages(@RequestBody AnthropicMessageRequest request,
                                                             HttpServletRequest http,
                                                             HttpServletResponse response) throws IOException {
        ApiKey apiKey = (ApiKey) http.getAttribute(DataPlaneAuthInterceptor.ATTR_API_KEY);
        String requestId = (String) http.getAttribute(RequestIdFilter.ATTR);
        if (Boolean.TRUE.equals(request.getStream())) {
            messageService.streamMessages(request, apiKey, requestId, response);
            return null;
        }
        return ResponseEntity.ok(messageService.messages(request, apiKey, requestId));
    }

    @PostMapping("/v1/messages/count_tokens")
    public Map<String, Object> countTokens(@RequestBody AnthropicMessageRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input_tokens", messageService.countTokens(request));
        return result;
    }
}
