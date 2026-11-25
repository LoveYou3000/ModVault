package com.modvault.web.admin;

import com.modvault.common.ApiException;
import com.modvault.dal.entity.ApiKey;
import com.modvault.service.ApiKeyService;
import com.modvault.web.dto.ApiKeyCreateRequest;
import com.modvault.web.dto.ApiKeyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/v1/api-keys")
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;

    public AdminApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKeyResponse> list() {
        return apiKeyService.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ApiKeyCreateRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw ApiException.badRequest("name: field required");
        }
        OffsetDateTime expiresAt = parseExpiry(req.getExpiresAt());
        ApiKeyService.CreatedKey created = apiKeyService.create(req.getName().trim(), expiresAt);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", created.key().getId());
        response.put("name", created.key().getName());
        response.put("key", created.plaintext());
        response.put("key_prefix", created.key().getKeyPrefix());
        response.put("enabled", true);
        response.put("expires_at", created.key().getExpiresAt() != null ? created.key().getExpiresAt().toString() : null);
        response.put("created_at", created.key().getCreatedAt() != null ? created.key().getCreatedAt().toString() : null);
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable("id") String id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return ApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(key.getKeyPrefix())
                .enabled(key.isEnabled())
                .expiresAt(key.getExpiresAt() != null ? key.getExpiresAt().toString() : null)
                .lastUsedAt(key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : null)
                .createdAt(key.getCreatedAt() != null ? key.getCreatedAt().toString() : null)
                .build();
    }

    private OffsetDateTime parseExpiry(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("expires_at: invalid ISO-8601 timestamp");
        }
    }
}
