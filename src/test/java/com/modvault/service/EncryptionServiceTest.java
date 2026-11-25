package com.modvault.service;

import com.modvault.config.ModVaultProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionServiceTest {

    private EncryptionService serviceWithKey(String base64Key) {
        ModVaultProperties props = new ModVaultProperties();
        props.setMasterKey(base64Key);
        return new EncryptionService(props);
    }

    private static String validKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void roundTrip() {
        EncryptionService svc = serviceWithKey(validKey());
        String ciphertext = svc.encrypt("sk-secret-value", "upstream:key");
        assertTrue(ciphertext.startsWith("v1:"));
        assertEquals("sk-secret-value", svc.decrypt(ciphertext, "upstream:key"));
    }

    @Test
    void differentAadFails() {
        EncryptionService svc = serviceWithKey(validKey());
        String ciphertext = svc.encrypt("sk-secret", "upstream-a:key-a");
        assertThrows(IllegalStateException.class, () -> svc.decrypt(ciphertext, "upstream-b:key-b"));
    }

    @Test
    void tamperedCiphertextFails() {
        EncryptionService svc = serviceWithKey(validKey());
        String ciphertext = svc.encrypt("sk-secret", "upstream:key");
        String tampered = ciphertext.substring(0, ciphertext.length() - 2) + "AA";
        assertThrows(IllegalStateException.class, () -> svc.decrypt(tampered, "upstream:key"));
    }

    @Test
    void missingKeyFailsStartup() {
        assertThrows(IllegalStateException.class, () -> serviceWithKey(""));
    }

    @Test
    void wrongLengthKeyFailsStartup() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> serviceWithKey(shortKey));
    }

    @Test
    void invalidBase64FailsStartup() {
        assertThrows(IllegalStateException.class, () -> serviceWithKey("not-valid-base64!!!"));
    }
}
