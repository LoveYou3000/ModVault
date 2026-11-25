package com.modvault.translate;

import com.modvault.translate.model.AnthropicMessage;
import com.modvault.translate.model.AnthropicMessageRequest;

/**
 * Local token estimator (A-16): ASCII counts 4 chars/token, CJK/non-ASCII 1 char/token,
 * +4 overhead per message, +8 overhead for system. Used by both {@code /count_tokens}
 * and (later) streaming {@code message_start.usage.input_tokens}, so the two always agree.
 */
public final class TokenEstimator {

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokens = 0.0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            tokens += (c < 128) ? 0.25 : 1.0;
        }
        return (int) Math.ceil(tokens);
    }

    public int estimateInputTokens(AnthropicMessageRequest request) {
        int total = 0;
        String systemText = ContentText.extractSystemText(request.getSystem());
        if (systemText != null && !systemText.isEmpty()) {
            total += estimateTokens(systemText) + 8;
        }
        if (request.getMessages() != null) {
            for (AnthropicMessage m : request.getMessages()) {
                total += estimateTokens(ContentText.extractMessageText(m.getContent())) + 4;
            }
        }
        return Math.max(total, 1);
    }
}
