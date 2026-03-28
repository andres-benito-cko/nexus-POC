package com.checkout.nexus.transformer.validation;

import com.checkout.nexus.transformer.model.NexusTransaction;
import com.checkout.nexus.transformer.model.le.LeLinkedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes failed/invalid transactions to the Nexus DLQ topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqHandler {

    private static final String DLQ_TOPIC = "nexus.transactions.dlq";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends an invalid Nexus transaction to the DLQ with context.
     *
     * @param original the original LE transaction that produced the failure
     * @param failed   the partially-assembled NexusTransaction (may be null)
     * @param errors   list of validation error messages
     */
    public void sendToDlq(LeLinkedTransaction original, NexusTransaction failed, List<String> errors) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("leTransactionId", original.getId());
        envelope.put("actionId", original.getActionId());
        envelope.put("nexusTransaction", failed);
        envelope.put("errors", errors);
        envelope.put("timestamp", Instant.now().toString());

        String key = original.getActionId() != null ? original.getActionId() : original.getId();

        try {
            kafkaTemplate.send(DLQ_TOPIC, key, envelope);
            log.warn("Sent transaction to DLQ: actionId={}, errors={}", key, errors);
        } catch (Exception e) {
            log.error("Failed to send to DLQ: actionId={}", key, e);
        }
    }
}
