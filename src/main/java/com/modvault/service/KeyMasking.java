package com.modvault.service;

/**
 * Masking helpers so raw secrets never leave the platform.
 */
public final class KeyMasking {

    private KeyMasking() {
    }

    /**
     * FR-22: first 3 chars + {@code ...} + last 4 chars.
     */
    public static String mask(String secret) {
        if (secret == null) {
            return null;
        }
        if (secret.length() <= 7) {
            return "***";
        }
        return secret.substring(0, 3) + "..." + secret.substring(secret.length() - 4);
    }
}
