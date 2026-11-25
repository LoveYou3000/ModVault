package com.modvault.translate;

import java.util.List;

/**
 * Shared OpenAI {@code finish_reason} → Anthropic {@code stop_reason} mapping (A.2).
 */
public final class FinishReasonMapper {

    private FinishReasonMapper() {
    }

    public static String map(String reason, List<String> warnings) {
        if (reason == null) {
            warnings.add("finish_reason null; mapped to end_turn");
            return "end_turn";
        }
        if ("stop".equals(reason)) {
            return "end_turn";
        }
        if ("length".equals(reason)) {
            return "max_tokens";
        }
        if ("tool_calls".equals(reason)) {
            return "tool_use";
        }
        if ("content_filter".equals(reason)) {
            return "refusal";
        }
        warnings.add("unknown finish_reason: " + reason);
        return "end_turn";
    }
}
