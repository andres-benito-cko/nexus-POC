package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Map;

/**
 * Spring {@link Configuration} that loads the Nexus engine config YAML into a
 * {@link ConfigHolder} and polls nexus-api every 10 s for an updated active config.
 *
 * <p>Resolution order at startup:
 * <ol>
 *   <li>Try {@code GET {nexus.api.url}/configs/active} — allows runtime config via nexus-api</li>
 *   <li>Fall back to {@code nexus-engine-config.yaml} on the classpath</li>
 * </ol>
 *
 * <p>After startup the {@link #pollActiveConfig()} scheduled task repeats the same resolution
 * and swaps the holder whenever the active version changes.
 */
@Slf4j
@Configuration
@EnableScheduling
public class EngineConfig {

    @Value("${nexus.api.url:}")
    private String nexusApiUrl;

    private ConfigHolder configHolder;

    @Bean
    public ConfigHolder configHolder() throws Exception {
        NexusEngineConfig initial = StringUtils.hasText(nexusApiUrl)
                ? loadFromApiOrClasspath()
                : loadFromClasspath();
        configHolder = new ConfigHolder(initial);
        return configHolder;
    }

    @Scheduled(fixedDelayString = "${nexus.config.poll-interval-ms:10000}")
    public void pollActiveConfig() {
        if (!StringUtils.hasText(nexusApiUrl) || configHolder == null) {
            return;
        }
        try {
            NexusEngineConfig fetched = tryLoadFromApi();
            if (fetched != null && !fetched.getVersion().equals(configHolder.getVersion())) {
                configHolder.update(fetched);
                log.info("NexusEngineConfig hot-swapped to version={}", fetched.getVersion());
            }
        } catch (Exception e) {
            log.warn("Config poll failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private NexusEngineConfig loadFromApiOrClasspath() throws Exception {
        NexusEngineConfig remote = tryLoadFromApi();
        return remote != null ? remote : loadFromClasspath();
    }

    @SuppressWarnings("unchecked")
    private NexusEngineConfig tryLoadFromApi() {
        try {
            RestTemplate rt = new RestTemplate();
            String url = nexusApiUrl + "/configs/active";
            ResponseEntity<Map> response = rt.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object content = response.getBody().get("content");
                if (content instanceof String yamlContent) {
                    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
                    NexusEngineConfig cfg = yamlMapper.readValue(yamlContent, NexusEngineConfig.class);
                    log.info("Loaded NexusEngineConfig from nexus-api ({}): version={}", nexusApiUrl, cfg.getVersion());
                    return cfg;
                }
            }
        } catch (Exception e) {
            log.warn("Could not load config from nexus-api ({}), falling back to classpath: {}", nexusApiUrl, e.getMessage());
        }
        return null;
    }

    private NexusEngineConfig loadFromClasspath() throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            if (in == null) {
                throw new IllegalStateException("nexus-engine-config.yaml not found on classpath");
            }
            NexusEngineConfig cfg = yamlMapper.readValue(in, NexusEngineConfig.class);
            log.info("Loaded NexusEngineConfig from classpath: version={}", cfg.getVersion());
            return cfg;
        }
    }
}
