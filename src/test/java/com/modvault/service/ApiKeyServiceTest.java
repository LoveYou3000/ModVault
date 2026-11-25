package com.modvault.service;

import com.modvault.dal.entity.ApiKey;
import com.modvault.dal.mapper.ApiKeyMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final ApiKeyMapper mapper = mock(ApiKeyMapper.class);
    private final ApiKeyService service = new ApiKeyService(mapper);

    @Test
    void createReturnsPlaintextOnceAndStoresHashAndPrefix() {
        ApiKeyService.CreatedKey created = service.create("team-a", null);
        assertTrue(created.plaintext().startsWith("sk-mv-"));
        assertEquals(8, created.key().getKeyPrefix().length());
        assertEquals(ApiKeyService.sha256Hex(created.plaintext()), created.key().getKeyHash());
        verify(mapper).insert(any(ApiKey.class));
    }

    @Test
    void authenticateReturnsEnabledKey() {
        String plain = "sk-mv-abcdefgh";
        ApiKey key = new ApiKey();
        key.setEnabled(true);
        when(mapper.findByHash(ApiKeyService.sha256Hex(plain))).thenReturn(key);
        assertSame(key, service.authenticate(plain));
    }

    @Test
    void authenticateRejectsRevokedKey() {
        ApiKey key = new ApiKey();
        key.setEnabled(false);
        when(mapper.findByHash(any())).thenReturn(key);
        assertNull(service.authenticate("sk-mv-abcdefgh"));
    }

    @Test
    void authenticateRejectsNonSkMvKey() {
        assertNull(service.authenticate("not-a-gateway-key"));
    }
}
