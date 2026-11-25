package com.modvault.service;

import com.modvault.common.ApiException;
import com.modvault.common.Ids;
import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.mapper.ModelMappingMapper;
import com.modvault.dal.mapper.UpstreamMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Model-mapping management. At most one enabled mapping per Anthropic model (A-18).
 */
@Service
public class ModelMappingService {

    private final ModelMappingMapper mapper;
    private final UpstreamMapper upstreamMapper;

    public ModelMappingService(ModelMappingMapper mapper, UpstreamMapper upstreamMapper) {
        this.mapper = mapper;
        this.upstreamMapper = upstreamMapper;
    }

    public ModelMapping create(String anthropicModel, UUID upstreamId, String upstreamModel, boolean enabled) {
        validateModel(anthropicModel);
        if (upstreamMapper.findById(upstreamId) == null) {
            throw ApiException.badRequest("upstream_id: upstream not found");
        }
        if (upstreamModel == null || upstreamModel.trim().isEmpty()) {
            throw ApiException.badRequest("upstream_model: field required");
        }
        if (enabled && mapper.countEnabledByModel(anthropicModel) > 0) {
            throw ApiException.conflict("an enabled mapping already exists for model '" + anthropicModel + "'");
        }
        ModelMapping mapping = new ModelMapping();
        mapping.setId(Ids.uuid());
        mapping.setAnthropicModel(anthropicModel);
        mapping.setUpstreamId(upstreamId);
        mapping.setUpstreamModel(upstreamModel);
        mapping.setEnabled(enabled);
        mapper.insert(mapping);
        return mapping;
    }

    public ModelMapping resolve(String anthropicModel) {
        return mapper.findEnabledByAnthropicModel(anthropicModel);
    }

    public ModelMapping findById(UUID id) {
        return mapper.findById(id);
    }

    public List<ModelMapping> findAll() {
        return mapper.findAll();
    }

    public void update(UUID id, UUID upstreamId, String upstreamModel, Boolean enabled) {
        ModelMapping existing = mapper.findById(id);
        if (existing == null) {
            throw ApiException.notFound("model mapping not found");
        }
        if (upstreamId != null && upstreamMapper.findById(upstreamId) == null) {
            throw ApiException.badRequest("upstream_id: upstream not found");
        }
        boolean newEnabled = enabled != null ? enabled : existing.isEnabled();
        if (newEnabled) {
            ModelMapping current = mapper.findEnabledByAnthropicModel(existing.getAnthropicModel());
            if (current != null && !current.getId().equals(id)) {
                throw ApiException.conflict("an enabled mapping already exists for model '" + existing.getAnthropicModel() + "'");
            }
        }
        if (upstreamId != null) {
            existing.setUpstreamId(upstreamId);
        }
        if (upstreamModel != null) {
            existing.setUpstreamModel(upstreamModel);
        }
        existing.setEnabled(newEnabled);
        mapper.update(existing);
    }

    public void delete(UUID id) {
        if (mapper.findById(id) == null) {
            throw ApiException.notFound("model mapping not found");
        }
        mapper.delete(id);
    }

    private void validateModel(String anthropicModel) {
        if (anthropicModel == null || anthropicModel.trim().isEmpty()) {
            throw ApiException.badRequest("anthropic_model: field required");
        }
        if (anthropicModel.length() > 200) {
            throw ApiException.badRequest("anthropic_model: length must be <= 200");
        }
    }
}
