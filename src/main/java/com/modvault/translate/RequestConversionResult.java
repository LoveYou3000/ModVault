package com.modvault.translate;

import com.modvault.translate.model.OpenAiChatRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of the request conversion: the OpenAI request, the estimated input tokens,
 * and non-fatal warnings that the caller should log.
 */
public class RequestConversionResult {

    private final OpenAiChatRequest request;
    private final List<String> warnings;
    private final int estimatedInputTokens;

    public RequestConversionResult(OpenAiChatRequest request, List<String> warnings, int estimatedInputTokens) {
        this.request = request;
        this.warnings = warnings == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(warnings));
        this.estimatedInputTokens = estimatedInputTokens;
    }

    public OpenAiChatRequest request() {
        return request;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int estimatedInputTokens() {
        return estimatedInputTokens;
    }
}
