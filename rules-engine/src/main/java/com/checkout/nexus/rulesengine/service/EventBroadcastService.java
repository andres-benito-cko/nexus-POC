package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.LeEventRecord;
import com.checkout.nexus.rulesengine.repository.LeEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final LeEventRepository leEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "le.linked.transactions", groupId = "rules-engine-ws",
            containerFactory = "leEventContainerFactory")
    public void onLeEvent(Map<String, Object> leEvent) {
        log.debug("Broadcasting LE event to WebSocket");
        messagingTemplate.convertAndSend("/topic/le-events", leEvent);
        try {
            String actionId = String.valueOf(leEvent.get("action_id"));
            if (actionId != null && !actionId.equals("null")) {
                String rawJson = objectMapper.writeValueAsString(leEvent);
                leEventRepository.save(LeEventRecord.builder()
                    .actionId(actionId)
                    .rawJson(rawJson)
                    .build());
            }
        } catch (Exception e) {
            log.warn("Failed to persist LE event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "nexus.blocks", groupId = "rules-engine-ws-nexus",
            containerFactory = "nexusContainerFactory")
    public void onNexusEvent(NexusBlock nexusEvent) {
        log.debug("Broadcasting Nexus event to WebSocket");
        messagingTemplate.convertAndSend("/topic/nexus-events", nexusEvent);
    }

    @KafkaListener(topics = "nexus.ledger.entries", groupId = "rules-engine-ws-ledger",
            containerFactory = "kafkaListenerContainerFactory")
    public void onLedgerEvent(LedgerEntryMessage ledgerEntry) {
        log.debug("Broadcasting ledger entry to WebSocket");
        messagingTemplate.convertAndSend("/topic/ledger-entries", ledgerEntry);
    }
}
