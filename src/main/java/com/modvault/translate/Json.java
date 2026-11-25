package com.modvault.translate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson mapper. ObjectMapper is thread-safe once configured.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
