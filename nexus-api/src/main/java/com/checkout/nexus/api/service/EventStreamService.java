package com.checkout.nexus.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler that broadcasts live Nexus transaction and DLQ events.
 * Also keeps a bounded in-memory buffer of recent Nexus transactions.
 */
@Slf4j
@Service
public class EventStreamService extends TextWebSocketHandler {

    private static final int MAX_BLOCKS = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Deque<String> recentBlocks = new ArrayDeque<>();
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${nexus.le.topic:le.linked.transactions}")
    private String leTopic;

    public EventStreamService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // ------------------------------------------------------------------ WebSocketHandler

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
    }

    // ------------------------------------------------------------------ Kafka consumers

    @KafkaListener(topics = "nexus.blocks", groupId = "nexus-api-live")
    public void onNexusBlock(String payload) {
        synchronized (recentBlocks) {
            recentBlocks.addFirst(payload);
            while (recentBlocks.size() > MAX_BLOCKS) {
                recentBlocks.removeLast();
            }
        }
        broadcast("NEXUS_BLOCK", payload);
    }

    // ------------------------------------------------------------------ Public API

    public void broadcast(String type, String payloadJson) {
        String message = "{\"type\":\"" + type + "\",\"payload\":" + payloadJson + "}";
        TextMessage msg = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (Exception e) {
                    log.warn("Failed to send to WebSocket session {}: {}", session.getId(), e.getMessage());
                    sessions.remove(session);
                }
            }
        }
    }

    public List<String> getRecentBlocks(int limit) {
        synchronized (recentBlocks) {
            List<String> result = new ArrayList<>();
            int count = 0;
            for (String tx : recentBlocks) {
                if (count++ >= limit) break;
                result.add(tx);
            }
            return result;
        }
    }

    public void replayToLe(String lePayloadJson) {
        kafkaTemplate.send(leTopic, lePayloadJson);
        log.info("Replayed LE event to topic {}", leTopic);
    }
}
