package com.modvault.service;

import com.modvault.config.ModVaultProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * AES-256-GCM encryption for upstream provider keys. Ciphertext is {@code v1:} + base64(iv|ct);
 * AAD binds the ciphertext to its upstream + key row. Fails application startup when the
 * master key is missing or not 32 bytes (FR-22).
 */
@Service
public class EncryptionService {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public EncryptionService(ModVaultProperties props) {
        String encoded = props.getMasterKey();
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalStateException("MODVAULT_MASTER_KEY is not configured (expected base64-encoded 32 bytes)");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MODVAULT_MASTER_KEY is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("MODVAULT_MASTER_KEY must decode to 32 bytes, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String aad) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + ct.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ct, 0, combined, IV_LENGTH, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("provider key encryption failed", e);
        }
    }

    public String decrypt(String ciphertext, String aad) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException("unsupported ciphertext format");
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ciphertext is not valid base64", e);
        }
        if (combined.length <= IV_LENGTH) {
            throw new IllegalStateException("ciphertext too short");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
        byte[] ct = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decryption failed (tampered ciphertext or wrong master key)", e);
        }
    }

    public static String aad(UUID upstreamId, UUID keyId) {
        return upstreamId + ":" + keyId;
    }
}
