package com.modvault.service;

import com.modvault.config.ModVaultProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates the static admin bearer token (MODVAULT_ADMIN_TOKEN). Requires >= 32 chars;
 * application startup fails without it so the admin plane is never unauthenticated (FR-24).
 */
@Component
public class AdminTokenValidator {

    private final byte[] expectedHash;

    public AdminTokenValidator(ModVaultProperties props) {
        String token = props.getAdminToken();
        if (token == null || token.length() < 32) {
            throw new IllegalStateException("MODVAULT_ADMIN_TOKEN must be configured with >= 32 characters");
        }
        this.expectedHash = sha256(token);
    }

    public boolean matches(String presented) {
        if (presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
