package com.modvault.upstream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiClientUrlTest {

    @Test
    void baseUrlWithV1NotDuplicated() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiClient.buildUrl("https://api.example.com/v1", true));
    }

    @Test
    void baseUrlWithPathPrefixKeepsPrefix() {
        assertEquals("https://gateway.example.com/openai/chat/completions",
                OpenAiClient.buildUrl("https://gateway.example.com/openai", true));
    }

    @Test
    void bareOriginAppendsV1() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiClient.buildUrl("https://api.example.com", true));
    }

    @Test
    void bareOriginWithoutAutoAppendV1() {
        assertEquals("https://api.example.com/chat/completions",
                OpenAiClient.buildUrl("https://api.example.com", false));
    }

    @Test
    void trailingSlashHandled() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiClient.buildUrl("https://api.example.com/v1/", true));
    }
}
