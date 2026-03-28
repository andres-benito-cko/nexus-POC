package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Map;

/**
 * Spring {@link Configuration} that loads the Nexus engine config YAML.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Try {@code GET {nexus.api.url}/configs/active} — allows runtime config via nexus-api</li>
 *   <li>Fall back to {@code nexus-engine-config.yaml} on the classpath</li>
 * </ol>
 */
@Slf4j
@Configuration
public class EngineConfig {

    @Value("${nexus.api.url:}")
    private String nexusApiUrl;

    @Bean
    public NexusEngineConfig nexusEngineConfig() throws Exception {
        if (StringUtils.hasText(nexusApiUrl)) {
            NexusEngineConfig remote = tryLoadFromApi();
            if (remote != null) {
                return remote;
            }
        }
        return loadFromClasspath();
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
