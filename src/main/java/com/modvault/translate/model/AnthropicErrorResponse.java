package com.modvault.translate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic error body: {@code {"type":"error","error":{...}}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicErrorResponse {

    private String type;

    private AnthropicError error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnthropicError {

        private String type;

        private String message;
    }

    public static AnthropicErrorResponse of(String type, String message) {
        return AnthropicErrorResponse.builder()
                .type("error")
                .error(AnthropicError.builder().type(type).message(message).build())
                .build();
    }
}
