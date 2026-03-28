package com.checkout.nexus.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Proxies test-bench requests to nexus-transformer's dry-run endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/test-bench")
public class TestBenchController {

    private final RestTemplate restTemplate;
    private final String transformerUrl;

    public TestBenchController(
            RestTemplate restTemplate,
            @Value("${nexus.transformer.url}") String transformerUrl) {
        this.restTemplate = restTemplate;
        this.transformerUrl = transformerUrl;
    }

    @PostMapping
    public ResponseEntity<Map> runTest(@RequestBody Map<String, Object> leEvent) {
        try {
            String url = transformerUrl + "/transform/test";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, leEvent, Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("Test bench proxy failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "errors", java.util.List.of("Transformer unreachable: " + e.getMessage())
            ));
        }
    }
}
