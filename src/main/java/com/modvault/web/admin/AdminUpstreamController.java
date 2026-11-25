package com.modvault.web.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.modvault.common.ApiException;
import com.modvault.dal.entity.Upstream;
import com.modvault.dal.entity.UpstreamKey;
import com.modvault.service.UpstreamService;
import com.modvault.translate.Json;
import com.modvault.web.dto.SetKeyRequest;
import com.modvault.web.dto.UpstreamRequest;
import com.modvault.web.dto.UpstreamResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/v1/upstreams")
public class AdminUpstreamController {

    private final UpstreamService upstreamService;

    public AdminUpstreamController(UpstreamService upstreamService) {
        this.upstreamService = upstreamService;
    }

    @GetMapping
    public List<UpstreamResponse> list() {
        return upstreamService.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<UpstreamResponse> create(@RequestBody UpstreamRequest req) {
        Upstream upstream = new Upstream();
        upstream.setName(req.getName());
        upstream.setBaseUrl(req.getBaseUrl());
        upstream.setEnabled(req.getEnabled() == null || req.getEnabled());
        upstream.setConnectTimeoutMs(req.getConnectTimeoutMs());
        upstream.setReadTimeoutMs(req.getReadTimeoutMs());
        upstream.setExtraHeaders(toJsonString(req.getExtraHeaders()));
        upstream.setMaxTokensField(req.getMaxTokensField() == null ? "max_tokens" : req.getMaxTokensField());
        return ResponseEntity.status(201).body(toResponse(upstreamService.create(upstream)));
    }

    @GetMapping("/{id}")
    public UpstreamResponse get(@PathVariable("id") String id) {
        Upstream upstream = upstreamService.findById(id);
        if (upstream == null) {
            throw ApiException.notFound("upstream not found");
        }
        return toResponse(upstream);
    }

    @PatchMapping("/{id}")
    public UpstreamResponse update(@PathVariable("id") String id, @RequestBody UpstreamRequest req) {
        Upstream existing = upstreamService.findById(id);
        if (existing == null) {
            throw ApiException.notFound("upstream not found");
        }
        if (req.getName() != null) {
            existing.setName(req.getName());
        }
        if (req.getBaseUrl() != null) {
            existing.setBaseUrl(req.getBaseUrl());
        }
        if (req.getEnabled() != null) {
            existing.setEnabled(req.getEnabled());
        }
        if (req.getConnectTimeoutMs() != null) {
            existing.setConnectTimeoutMs(req.getConnectTimeoutMs());
        }
        if (req.getReadTimeoutMs() != null) {
            existing.setReadTimeoutMs(req.getReadTimeoutMs());
        }
        if (req.getExtraHeaders() != null) {
            existing.setExtraHeaders(toJsonString(req.getExtraHeaders()));
        }
        if (req.getMaxTokensField() != null) {
            existing.setMaxTokensField(req.getMaxTokensField());
        }
        upstreamService.update(existing);
        return toResponse(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        upstreamService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/key")
    public Map<String, String> setKey(@PathVariable("id") String id, @RequestBody SetKeyRequest req) {
        if (req.getKey() == null || req.getKey().trim().isEmpty()) {
            throw ApiException.badRequest("key: field required");
        }
        UpstreamKey key = upstreamService.setKey(id, req.getKey().trim());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("key_masked", key.getKeyMasked());
        return result;
    }

    private UpstreamResponse toResponse(Upstream u) {
        UpstreamKey key = upstreamService.latestKey(u.getId());
        return UpstreamResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .baseUrl(u.getBaseUrl())
                .enabled(u.isEnabled())
                .connectTimeoutMs(u.getConnectTimeoutMs())
                .readTimeoutMs(u.getReadTimeoutMs())
                .extraHeaders(toJsonNode(u.getExtraHeaders()))
                .maxTokensField(u.getMaxTokensField())
                .keyMasked(key != null ? key.getKeyMasked() : null)
                .createdAt(iso(u.getCreatedAt()))
                .updatedAt(iso(u.getUpdatedAt()))
                .build();
    }

    private static String iso(OffsetDateTime dt) {
        return dt != null ? dt.toString() : null;
    }

    private static String toJsonString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private static JsonNode toJsonNode(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Json.mapper().readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
