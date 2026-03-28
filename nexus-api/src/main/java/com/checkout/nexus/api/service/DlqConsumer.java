package com.checkout.nexus.api.service;

import com.checkout.nexus.api.entity.DlqEventEntity;
import com.checkout.nexus.api.repository.DlqEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Consumes the DLQ topic and persists events to Postgres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqConsumer {

    private final DlqEventRepository dlqEventRepository;
    private final EventStreamService eventStreamService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @KafkaListener(topics = "nexus.blocks.dlq", groupId = "nexus-api-dlq")
    public void onDlqEvent(String payload) {
        try {
            Map<String, Object> envelope = MAPPER.readValue(payload, new TypeReference<>() {});

            String actionId = (String) envelope.get("actionId");
            Object errorsObj = envelope.get("errors");
            String errors = errorsObj != null ? MAPPER.writeValueAsString(errorsObj) : null;

            DlqEventEntity entity = new DlqEventEntity();
            entity.setActionId(actionId);
            entity.setPayload(payload);
            entity.setErrors(errors);
            dlqEventRepository.save(entity);

            log.warn("DLQ event stored: actionId={}", actionId);
            eventStreamService.broadcast("DLQ_EVENT", payload);
        } catch (Exception e) {
            log.error("Failed to process DLQ event", e);
        }
    }

    public void replay(DlqEventEntity event, String lePayloadJson) {
        eventStreamService.replayToLe(lePayloadJson);
    }
}
