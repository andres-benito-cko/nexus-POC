package com.checkout.nexus.api.service;

import com.checkout.nexus.api.entity.EngineConfigEntity;
import com.checkout.nexus.api.repository.EngineConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final EngineConfigRepository configRepository;
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public List<EngineConfigEntity> listAll() {
        return configRepository.findAll();
    }

    public Optional<EngineConfigEntity> findById(UUID id) {
        return configRepository.findById(id);
    }

    public Optional<EngineConfigEntity> findActive() {
        return configRepository.findByActiveTrue();
    }

    @Transactional
    public EngineConfigEntity create(String version, String content, String createdBy) {
        validateYaml(content);
        EngineConfigEntity entity = new EngineConfigEntity();
        entity.setVersion(version);
        entity.setContent(content);
        entity.setCreatedBy(createdBy);
        entity.setActive(false);
        return configRepository.save(entity);
    }

    @Transactional
    public EngineConfigEntity update(UUID id, String version, String content) {
        EngineConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + id));
        validateYaml(content);
        entity.setVersion(version);
        entity.setContent(content);
        return configRepository.save(entity);
    }

    @Transactional
    public EngineConfigEntity activate(UUID id) {
        // Deactivate any currently active config
        configRepository.findByActiveTrue().ifPresent(current -> {
            current.setActive(false);
            current.setValidTo(Instant.now());
            configRepository.save(current);
        });

        EngineConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + id));
        entity.setActive(true);
        entity.setValidFrom(Instant.now());
        entity.setValidTo(null);
        EngineConfigEntity saved = configRepository.save(entity);
        log.info("Activated engine config id={} version={}", id, entity.getVersion());
        return saved;
    }

    /**
     * Validates a YAML content string without persisting it.
     * Returns a map with "valid" (boolean) and optionally "error" (string).
     */
    public Map<String, Object> validate(String content) {
        try {
            validateYaml(content);
            return Map.of("valid", true);
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }

    private void validateYaml(String content) {
        try {
            YAML_MAPPER.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
        }
    }
}
