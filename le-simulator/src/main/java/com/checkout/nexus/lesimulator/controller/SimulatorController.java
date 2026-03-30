package com.checkout.nexus.lesimulator.controller;

import com.checkout.nexus.lesimulator.model.SchemeProfile;
import com.checkout.nexus.lesimulator.service.ScenarioLoader;
import com.checkout.nexus.lesimulator.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/simulate")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final ScenarioLoader scenarioLoader;

    @GetMapping("/scenarios")
    public List<ScenarioLoader.ScenarioInfo> listScenarios() {
        return scenarioLoader.listScenarios();
    }

    /**
     * Plays a named scenario.
     *
     * Request body (all optional):
     * <pre>{ "delayMs": 500, "scheme": "Carte Bancaire" }</pre>
     *
     * {@code scheme} must match a {@link SchemeProfile#schemeName()} (case-insensitive).
     * Defaults to Visa if omitted or unrecognised.
     */
    @PostMapping("/scenario/{id}")
    public ResponseEntity<Map<String, String>> playScenario(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        int delayMs    = body != null && body.containsKey("delayMs")
            ? ((Number) body.get("delayMs")).intValue() : 500;
        String schemeName = body != null ? (String) body.get("scheme") : null;
        SchemeProfile scheme = resolveScheme(schemeName);
        simulatorService.playScenario(id, delayMs, scheme);
        return ResponseEntity.ok(Map.of("status", "started", "scenarioId", id, "scheme", scheme.schemeName()));
    }

    @PostMapping("/random/start")
    public ResponseEntity<Map<String, String>> startRandom(
            @RequestBody(required = false) Map<String, Integer> body) {
        int intervalMs = (body != null && body.containsKey("intervalMs")) ? body.get("intervalMs") : 2000;
        simulatorService.startRandom(intervalMs);
        return ResponseEntity.ok(Map.of("status", "random_started"));
    }

    @PostMapping("/random/stop")
    public ResponseEntity<Map<String, String>> stopRandom() {
        simulatorService.stopRandom();
        return ResponseEntity.ok(Map.of("status", "random_stopped"));
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return simulatorService.getStatus();
    }

    /** Returns all supported scheme profiles so callers know valid scheme names. */
    @GetMapping("/schemes")
    public List<Map<String, String>> listSchemes() {
        return SchemeProfile.ALL.stream()
            .map(p -> Map.of("schemeName", p.schemeName(), "defaultCurrency", p.defaultCurrency()))
            .toList();
    }

    private SchemeProfile resolveScheme(String name) {
        if (name == null) return SchemeProfile.VISA;
        return SchemeProfile.ALL.stream()
            .filter(p -> p.schemeName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(SchemeProfile.VISA);
    }
}
