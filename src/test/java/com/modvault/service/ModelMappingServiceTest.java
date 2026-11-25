package com.modvault.service;

import com.modvault.common.ApiException;
import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.entity.Upstream;
import com.modvault.dal.mapper.ModelMappingMapper;
import com.modvault.dal.mapper.UpstreamMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelMappingServiceTest {

    private final ModelMappingMapper mapper = mock(ModelMappingMapper.class);
    private final UpstreamMapper upstreamMapper = mock(UpstreamMapper.class);
    private final ModelMappingService service = new ModelMappingService(mapper, upstreamMapper);

    @Test
    void createRejectsDuplicateEnabledMapping() {
        when(upstreamMapper.findById(any(UUID.class))).thenReturn(new Upstream());
        when(mapper.countEnabledByModel("claude")).thenReturn(1);
        ApiException e = assertThrows(ApiException.class,
                () -> service.create("claude", UUID.randomUUID(), "gpt-4o", true));
        assertTrue(e.getMessage().contains("enabled mapping already exists"));
    }

    @Test
    void createSucceedsWhenNoConflict() {
        when(upstreamMapper.findById(any(UUID.class))).thenReturn(new Upstream());
        when(mapper.countEnabledByModel("claude")).thenReturn(0);
        ModelMapping mapping = service.create("claude", UUID.randomUUID(), "gpt-4o", true);
        assertNotNull(mapping.getId());
        verify(mapper).insert(any(ModelMapping.class));
    }

    @Test
    void createRejectsEmptyModel() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.create("", UUID.randomUUID(), "gpt-4o", true));
        assertTrue(e.getMessage().contains("anthropic_model"));
    }

    @Test
    void createRejectsUnknownUpstream() {
        when(upstreamMapper.findById(any(UUID.class))).thenReturn(null);
        assertThrows(ApiException.class,
                () -> service.create("claude", UUID.randomUUID(), "gpt-4o", true));
    }
}
