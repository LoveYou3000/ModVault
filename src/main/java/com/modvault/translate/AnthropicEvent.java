package com.modvault.translate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One Anthropic streaming event: an {@code event:} name plus the {@code data:} payload.
 */
public class AnthropicEvent {

    private final String eventType;
    private final JsonNode data;

    public AnthropicEvent(String eventType, JsonNode data) {
        this.eventType = eventType;
        this.data = data;
    }

    public String eventType() {
        return eventType;
    }

    public JsonNode data() {
        return data;
    }

    /** Serializes to a full SSE frame (event + data + blank line). */
    public String toSse() {
        return "event: " + eventType + "\ndata: " + data.toString() + "\n\n";
    }
}
