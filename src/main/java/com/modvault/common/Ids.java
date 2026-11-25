package com.modvault.common;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Random identifier generation. Shared SecureRandom avoids per-call seeding cost.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Ids() {
    }

    public static String requestId() {
        return "req_" + randomHex(24);
    }

    public static String messageId() {
        return "msg_" + randomHex(24);
    }

    /** Gateway API key: {@code sk-mv-} + 32 random bytes (base64url, no padding). */
    public static String apiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sk-mv-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static String randomHex(int chars) {
        StringBuilder sb = new StringBuilder(chars);
        for (int i = 0; i < chars; i++) {
            sb.append(HEX[RANDOM.nextInt(16)]);
        }
        return sb.toString();
    }
}
