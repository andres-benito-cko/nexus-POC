package com.checkout.nexus.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Proxies resolver introspection requests to nexus-transformer.
 */
@Slf4j
@RestController
@RequestMapping("/resolvers")
public class ResolverController {

    private final RestTemplate restTemplate;
    private final String transformerUrl;

    public ResolverController(
            RestTemplate restTemplate,
            @Value("${nexus.transformer.url}") String transformerUrl) {
        this.restTemplate = restTemplate;
        this.transformerUrl = transformerUrl;
    }

    @GetMapping
    public ResponseEntity<List> getResolvers() {
        try {
            String url = transformerUrl + "/resolvers";
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.warn("Resolver proxy failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }
}
