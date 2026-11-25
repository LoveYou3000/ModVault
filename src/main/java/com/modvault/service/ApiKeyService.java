package com.modvault.service;

import com.modvault.common.Ids;
import com.modvault.dal.entity.ApiKey;
import com.modvault.dal.mapper.ApiKeyMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gateway API key lifecycle: create (returns the plaintext once), authenticate, revoke.
 * Only SHA-256 hash + 8-char prefix are persisted (FR-24).
 */
@Service
public class ApiKeyService {

    private final ApiKeyMapper mapper;

    public ApiKeyService(ApiKeyMapper mapper) {
        this.mapper = mapper;
    }

    public CreatedKey create(String name, OffsetDateTime expiresAt) {
        String plain = Ids.apiKey();
        ApiKey key = new ApiKey();
        key.setId(Ids.uuid());
        key.setName(name);
        key.setKeyHash(sha256Hex(plain));
        key.setKeyPrefix(plain.substring(0, 8));
        key.setEnabled(true);
        key.setExpiresAt(expiresAt);
        mapper.insert(key);
        return new CreatedKey(key, plain);
    }

    /**
     * Returns the matching, valid key, or {@code null} when the presented key is
     * missing, unknown, revoked, or expired.
     */
    public ApiKey authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isEmpty() || !presentedKey.startsWith("sk-mv-")) {
            return null;
        }
        ApiKey key = mapper.findByHash(sha256Hex(presentedKey));
        if (key == null || !key.isEnabled()) {
            return null;
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }
        return key;
    }

    public void revoke(String id) {
        mapper.revoke(id);
    }

    public List<ApiKey> findAll() {
        return mapper.findAll();
    }

    public void touchLastUsed(String id) {
        mapper.touchLastUsed(id);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Result of key creation: the entity (never re-exposed) plus the one-time plaintext. */
    public static class CreatedKey {
        private final ApiKey key;
        private final String plaintext;

        CreatedKey(ApiKey key, String plaintext) {
            this.key = key;
            this.plaintext = plaintext;
        }

        public ApiKey key() {
            return key;
        }

        public String plaintext() {
            return plaintext;
        }
    }
}
