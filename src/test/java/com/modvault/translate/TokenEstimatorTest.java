package com.modvault.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void asciiIsFourCharsPerToken() {
        assertEquals(1, estimator.estimateTokens("abcd"));
        assertEquals(2, estimator.estimateTokens("abcdefgh"));
        assertEquals(1, estimator.estimateTokens("abc")); // ceil(0.75)
    }

    @Test
    void cjkIsOneCharPerToken() {
        assertEquals(100, estimator.estimateTokens(repeat("汉", 100)));
    }

    @Test
    void cjkWithinTolerance() {
        int tokens = estimator.estimateTokens(repeat("汉", 100));
        assertTrue(tokens >= 80 && tokens <= 130, "tokens=" + tokens);
    }

    @Test
    void emptyTextIsZero() {
        assertEquals(0, estimator.estimateTokens(""));
        assertEquals(0, estimator.estimateTokens(null));
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
