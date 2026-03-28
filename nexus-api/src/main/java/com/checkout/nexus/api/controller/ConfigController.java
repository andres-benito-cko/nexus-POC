package com.checkout.nexus.api.controller;

import com.checkout.nexus.api.entity.EngineConfigEntity;
import com.checkout.nexus.api.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public List<EngineConfigEntity> listAll() {
        return configService.listAll();
    }

    @GetMapping("/active")
    public ResponseEntity<EngineConfigEntity> getActive() {
        return configService.findActive()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EngineConfigEntity> getById(@PathVariable UUID id) {
        return configService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EngineConfigEntity> create(@RequestBody Map<String, String> body) {
        String version = body.getOrDefault("version", "1.0");
        String content = body.get("content");
        String createdBy = body.getOrDefault("createdBy", "api");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(configService.create(version, content, createdBy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EngineConfigEntity> update(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        try {
            String version = body.getOrDefault("version", "1.0");
            String content = body.get("content");
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(configService.update(id, version, content));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<EngineConfigEntity> activate(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(configService.activate(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Map.of("valid", false, "error", "content is required");
        }
        return configService.validate(content);
    }
}
