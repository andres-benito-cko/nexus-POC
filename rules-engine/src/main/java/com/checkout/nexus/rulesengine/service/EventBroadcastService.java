package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
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

    @KafkaListener(topics = "le.linked.transactions", groupId = "rules-engine-ws",
            containerFactory = "leEventContainerFactory")
    public void onLeEvent(Map<String, Object> leEvent) {
        log.debug("Broadcasting LE event to WebSocket");
        messagingTemplate.convertAndSend("/topic/le-events", leEvent);
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
