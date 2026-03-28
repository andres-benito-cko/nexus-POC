package com.checkout.nexus.transformer.controller;

import com.checkout.nexus.transformer.engine.NexusEngine;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.resolver.ResolverRegistry;
import com.checkout.nexus.transformer.model.NexusTransaction;
import com.checkout.nexus.transformer.model.le.LeLinkedTransaction;
import com.checkout.nexus.transformer.validation.NexusValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes dry-run transformation and resolver introspection endpoints.
 * Used by nexus-api for the test bench and resolver listing features.
 */
@RestController
@RequiredArgsConstructor
public class TestBenchController {

    private final NexusEngine nexusEngine;
    private final NexusValidator nexusValidator;
    private final ResolverRegistry resolverRegistry;

    /**
     * Runs an LE transaction through the engine without publishing to Kafka.
     * Returns the resulting NexusTransaction plus any validation errors.
     */
    @PostMapping("/transform/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody LeLinkedTransaction le) {
        Map<String, Object> response = new HashMap<>();
        try {
            LeContext ctx = new LeContext(le);
            NexusTransaction tx = nexusEngine.transform(ctx);
            NexusValidator.ValidationResult result = nexusValidator.validate(tx);

            response.put("success", result.isValid());
            response.put("transaction", tx);
            if (!result.isValid()) {
                response.put("errors", result.getErrors());
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("errors", List.of("Transformation error: " + e.getMessage()));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Returns all registered resolver names (for display in the config editor).
     */
    @GetMapping("/resolvers")
    public List<Map<String, String>> getResolvers() {
        return resolverRegistry.getResolverNames().stream()
                .sorted()
                .map(name -> Map.of("name", name))
                .collect(Collectors.toList());
    }
}
