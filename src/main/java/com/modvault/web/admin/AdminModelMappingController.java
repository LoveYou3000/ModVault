package com.modvault.web.admin;

import com.modvault.dal.entity.ModelMapping;
import com.modvault.dal.entity.Upstream;
import com.modvault.service.ModelMappingService;
import com.modvault.service.UpstreamService;
import com.modvault.web.dto.ModelMappingRequest;
import com.modvault.web.dto.ModelMappingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/v1/model-mappings")
public class AdminModelMappingController {

    private final ModelMappingService mappingService;
    private final UpstreamService upstreamService;

    public AdminModelMappingController(ModelMappingService mappingService, UpstreamService upstreamService) {
        this.mappingService = mappingService;
        this.upstreamService = upstreamService;
    }

    @GetMapping
    public List<ModelMappingResponse> list() {
        return mappingService.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<ModelMappingResponse> create(@RequestBody ModelMappingRequest req) {
        ModelMapping mapping = mappingService.create(
                req.getAnthropicModel(), req.getUpstreamId(), req.getUpstreamModel(),
                req.getEnabled() == null || req.getEnabled());
        return ResponseEntity.status(201).body(toResponse(mapping));
    }

    @PatchMapping("/{id}")
    public ModelMappingResponse update(@PathVariable("id") UUID id, @RequestBody ModelMappingRequest req) {
        mappingService.update(id, req.getUpstreamId(), req.getUpstreamModel(), req.getEnabled());
        return toResponse(mappingService.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        mappingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ModelMappingResponse toResponse(ModelMapping m) {
        Upstream upstream = upstreamService.findById(m.getUpstreamId());
        return ModelMappingResponse.builder()
                .id(m.getId().toString())
                .anthropicModel(m.getAnthropicModel())
                .upstreamId(m.getUpstreamId().toString())
                .upstreamModel(m.getUpstreamModel())
                .enabled(m.isEnabled())
                .upstreamName(upstream != null ? upstream.getName() : null)
                .createdAt(iso(m.getCreatedAt()))
                .updatedAt(iso(m.getUpdatedAt()))
                .build();
    }

    private static String iso(OffsetDateTime dt) {
        return dt != null ? dt.toString() : null;
    }
}
