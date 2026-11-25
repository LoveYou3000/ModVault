package com.modvault.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyMaskingTest {

    @Test
    void masksLongSecret() {
        String mask = KeyMasking.mask("sk-abcdef1234567890");
        assertEquals("sk-...7890", mask);
    }

    @Test
    void masksShortSecret() {
        assertEquals("***", KeyMasking.mask("abc"));
    }

    @Test
    void nullInput() {
        assertNull(KeyMasking.mask(null));
    }
}
