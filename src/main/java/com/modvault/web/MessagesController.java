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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic-compatible data plane: {@code POST /v1/messages} and
 * {@code POST /v1/messages/count_tokens}.
 */
@RestController
public class MessagesController {

    private final MessageService messageService;

    public MessagesController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/v1/messages")
    public ResponseEntity<AnthropicMessageResponse> messages(@RequestBody AnthropicMessageRequest request,
                                                             HttpServletRequest http) {
        ApiKey apiKey = (ApiKey) http.getAttribute(DataPlaneAuthInterceptor.ATTR_API_KEY);
        String requestId = (String) http.getAttribute(RequestIdFilter.ATTR);
        AnthropicMessageResponse response = messageService.messages(request, apiKey, requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v1/messages/count_tokens")
    public Map<String, Object> countTokens(@RequestBody AnthropicMessageRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input_tokens", messageService.countTokens(request));
        return result;
    }
}
