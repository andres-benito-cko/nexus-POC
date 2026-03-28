package com.checkout.nexus.api.controller;

import com.checkout.nexus.api.entity.DlqEventEntity;
import com.checkout.nexus.api.repository.DlqEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqEventRepository dlqEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LE_TOPIC = "le.linked.transactions";

    @GetMapping
    public List<DlqEventEntity> listAll() {
        return dlqEventRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable UUID id) {
        return dlqEventRepository.findById(id).map(event -> {
            try {
                // Extract the original LE transaction from the DLQ envelope
                Map<String, Object> envelope = MAPPER.readValue(event.getPayload(), new TypeReference<>() {});
                Object leTransaction = envelope.get("leTransaction");
                if (leTransaction == null) {
                    // Try to use the whole payload if no leTransaction field
                    leTransaction = envelope;
                }
                String leJson = MAPPER.writeValueAsString(leTransaction);
                kafkaTemplate.send(LE_TOPIC, event.getActionId(), leJson);

                event.setReplayedAt(Instant.now());
                dlqEventRepository.save(event);

                log.info("Replayed DLQ event id={} actionId={}", id, event.getActionId());
                return ResponseEntity.ok(Map.<String, Object>of("replayed", true, "actionId", event.getActionId()));
            } catch (Exception e) {
                log.error("Failed to replay DLQ event id={}", id, e);
                return ResponseEntity.<Map<String, Object>>ok(Map.of("replayed", false, "error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
