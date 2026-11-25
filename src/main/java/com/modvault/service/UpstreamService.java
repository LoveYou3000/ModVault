package com.modvault.service;

import com.modvault.common.ApiException;
import com.modvault.common.Ids;
import com.modvault.dal.entity.Upstream;
import com.modvault.dal.entity.UpstreamKey;
import com.modvault.dal.mapper.ModelMappingMapper;
import com.modvault.dal.mapper.UpstreamKeyMapper;
import com.modvault.dal.mapper.UpstreamMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Upstream and provider-key management, including AES-256-GCM key storage and
 * single-enabled-key semantics (A-10).
 */
@Service
public class UpstreamService {

    private final UpstreamMapper mapper;
    private final UpstreamKeyMapper keyMapper;
    private final ModelMappingMapper mappingMapper;
    private final EncryptionService encryption;

    public UpstreamService(UpstreamMapper mapper, UpstreamKeyMapper keyMapper,
                           ModelMappingMapper mappingMapper, EncryptionService encryption) {
        this.mapper = mapper;
        this.keyMapper = keyMapper;
        this.mappingMapper = mappingMapper;
        this.encryption = encryption;
    }

    public Upstream create(Upstream upstream) {
        validate(upstream);
        upstream.setId(Ids.uuid());
        mapper.insert(upstream);
        return upstream;
    }

    public Upstream findById(UUID id) {
        return mapper.findById(id);
    }

    public List<Upstream> findAll() {
        return mapper.findAll();
    }

    public void update(Upstream upstream) {
        if (mapper.findById(upstream.getId()) == null) {
            throw ApiException.notFound("upstream not found");
        }
        validate(upstream);
        mapper.update(upstream);
    }

    private void validate(Upstream u) {
        if (u.getName() == null || u.getName().trim().isEmpty()) {
            throw ApiException.badRequest("name: field required");
        }
        if (u.getBaseUrl() == null || u.getBaseUrl().trim().isEmpty()) {
            throw ApiException.badRequest("base_url: field required");
        }
    }

    public void delete(UUID id) {
        if (mapper.findById(id) == null) {
            throw ApiException.notFound("upstream not found");
        }
        // block deletion when a model mapping still references this upstream (FR-21 → 409)
        List<com.modvault.dal.entity.ModelMapping> mappings = mappingMapper.findAll();
        for (com.modvault.dal.entity.ModelMapping m : mappings) {
            if (id.equals(m.getUpstreamId())) {
                throw ApiException.conflict("upstream is referenced by model mapping for '" + m.getAnthropicModel() + "'");
            }
        }
        mapper.delete(id);
    }

    /**
     * Idempotently replace the enabled provider key for an upstream (FR-22).
     */
    public UpstreamKey setKey(UUID upstreamId, String plaintextKey) {
        if (mapper.findById(upstreamId) == null) {
            throw ApiException.notFound("upstream not found");
        }
        UUID keyId = Ids.uuid();
        String aad = EncryptionService.aad(upstreamId, keyId);
        String ciphertext = encryption.encrypt(plaintextKey, aad);

        keyMapper.disableAllForUpstream(upstreamId);
        UpstreamKey key = new UpstreamKey();
        key.setId(keyId);
        key.setUpstreamId(upstreamId);
        key.setKeyCiphertext(ciphertext);
        key.setKeyMasked(KeyMasking.mask(plaintextKey));
        key.setEnabled(true);
        key.setStatus("active");
        keyMapper.insert(key);
        return key;
    }

    /**
     * Decrypts the enabled provider key for an upstream. A tampered ciphertext marks the
     * key unavailable and surfaces 500 {@code api_error} (never an empty-key upstream call).
     */
    public String resolvePlaintextKey(Upstream upstream) {
        UpstreamKey key = keyMapper.findEnabledByUpstreamId(upstream.getId());
        if (key == null) {
            throw ApiException.apiError("upstream has no enabled provider key");
        }
        try {
            String plain = encryption.decrypt(key.getKeyCiphertext(), EncryptionService.aad(upstream.getId(), key.getId()));
            keyMapper.touchLastUsed(key.getId());
            return plain;
        } catch (IllegalStateException e) {
            keyMapper.updateStatus(key.getId(), false, "unavailable");
            throw ApiException.apiError("provider key could not be decrypted");
        }
    }

    public UpstreamKey latestKey(UUID upstreamId) {
        return keyMapper.findLatestByUpstreamId(upstreamId);
    }
}
