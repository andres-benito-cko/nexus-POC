package com.checkout.nexus.generator.controller;

import com.checkout.nexus.generator.agent.AgentLoop;
import com.checkout.nexus.generator.model.GenerateRequest;
import com.checkout.nexus.generator.model.GenerateResponse;
import com.checkout.nexus.generator.model.ProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GenerateController {

    private final AgentLoop agentLoop;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@RequestBody GenerateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                GenerateResponse response = agentLoop.run(request.getPrompt(), progress -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(progress)));
                    } catch (Exception e) {
                        log.warn("Failed to send SSE progress: {}", e.getMessage());
                    }
                });

                String eventName = response.isSuccess() ? "result" : "error";
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(response)));
                emitter.complete();
            } catch (Exception e) {
                log.error("Generation failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"" + e.getMessage() + "\"}"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @PostMapping(value = "/generate", params = "sync=true", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerateResponse> generateSync(@RequestBody GenerateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().body(
                    GenerateResponse.builder()
                            .success(false)
                            .errors(List.of("Prompt is required"))
                            .build());
        }

        GenerateResponse response = agentLoop.run(request.getPrompt(), progress -> {
            log.info("Progress: [{}] {}", progress.getStep(), progress.getMessage());
        });

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenerateResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(
                GenerateResponse.builder()
                        .success(false)
                        .errors(List.of(e.getMessage()))
                        .build());
    }
}
