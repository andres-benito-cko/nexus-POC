package com.checkout.nexus.lesimulator.controller;

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

    @PostMapping("/scenario/{id}")
    public ResponseEntity<Map<String, String>> playScenario(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Integer> body) {
        int delayMs = (body != null && body.containsKey("delayMs")) ? body.get("delayMs") : 500;
        simulatorService.playScenario(id, delayMs);
        return ResponseEntity.ok(Map.of("status", "started", "scenarioId", id));
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
}
