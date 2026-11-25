package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Pure helpers for extracting text from Anthropic content shapes.
 */
public final class ContentText {

    private ContentText() {
    }

    /**
     * Extracts system text: a JSON string, or text blocks joined with {@code \n\n}
     * (A-06). Returns {@code null} when absent or empty.
     */
    public static String extractSystemText(JsonNode system) {
        if (system == null || system.isNull()) {
            return null;
        }
        if (system.isTextual()) {
            return system.textValue();
        }
        if (system.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : system) {
                if ("text".equals(block.path("type").asText())) {
                    String t = block.path("text").asText("");
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(t);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        return null;
    }

    /**
     * Extracts the textual content of a message: a JSON string, or text blocks
     * concatenated in order.
     */
    public static String extractMessageText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.textValue();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Extracts {@code metadata.user_id} as a string, or {@code null} when absent.
     */
    public static String extractUserId(JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            return null;
        }
        JsonNode userId = metadata.get("user_id");
        if (userId == null || userId.isNull()) {
            return null;
        }
        return userId.isTextual() ? userId.textValue() : userId.toString();
    }
}
