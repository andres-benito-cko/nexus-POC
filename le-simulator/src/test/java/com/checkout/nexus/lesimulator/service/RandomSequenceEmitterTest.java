package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RandomSequenceEmitterTest {

    @Mock
    KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;

    @Mock
    LeBatchConfig config;

    @Spy
    ScenarioLoader scenarioLoader;

    @InjectMocks
    RandomSequenceEmitter emitter;

    @BeforeEach
    void setUp() {
        when(config.getTopicName()).thenReturn("test.topic");
    }

    @Test
    void emitSequence_sends4Messages() throws InterruptedException {
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(eq("test.topic"), anyString(), any(LeLinkedTransaction.class));
    }

    @Test
    void emitSequence_allMessagesShareSameActionId() throws InterruptedException {
        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LeLinkedTransaction> txnCaptor = ArgumentCaptor.forClass(LeLinkedTransaction.class);

        emitter.emitSequence(0);

        verify(kafkaTemplate, times(4)).send(eq("test.topic"), keyCaptor.capture(), txnCaptor.capture());

        String firstKey = keyCaptor.getAllValues().get(0);
        keyCaptor.getAllValues().forEach(k -> assertEquals(firstKey, k, "Kafka partition keys must all be the same action_id"));

        String firstActionId = txnCaptor.getAllValues().get(0).getActionId();
        txnCaptor.getAllValues().forEach(v -> assertEquals(firstActionId, v.getActionId()));
    }

    @Test
    void emitSequence_versionsAreMonotonicallyIncreasing() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        List<LeLinkedTransaction> sent = captor.getAllValues();
        for (int i = 0; i < sent.size(); i++) {
            assertEquals(i + 1, sent.get(i).getTransactionVersion(),
                "Version " + (i + 1) + " was not in position " + i);
        }
    }

    @Test
    void emitSequence_v1HasOnlyGatewayEvents() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        LeLinkedTransaction v1 = captor.getAllValues().get(0);
        assertFalse(v1.getGatewayEvents().isEmpty(),          "v1 must have GW");
        assertTrue(v1.getCosEvents().isEmpty(),               "v1 must not have COS");
        assertTrue(v1.getBalancesChangedEvents().isEmpty(),   "v1 must not have Balances");
        assertTrue(v1.getSchemeSettlementEvents().isEmpty(),  "v1 must not have SD");
    }

    @Test
    void emitSequence_lastVersionHasSchemeSettlement() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        LeLinkedTransaction v4 = captor.getAllValues().get(3);
        assertFalse(v4.getSchemeSettlementEvents().isEmpty(), "v4 must be settled (has SD)");
    }
}
