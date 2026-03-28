package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.engine.resolver.ResolverRegistry;
import com.checkout.nexus.transformer.model.le.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StateMachineRunnerTest {

    private static NexusEngineConfig config;
    private static StateMachineRunner runner;

    @BeforeAll
    static void setup() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                        new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        try (var in = StateMachineRunnerTest.class.getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }
        ExpressionEvaluator evaluator = new ExpressionEvaluator(mock(ResolverRegistry.class));
        runner = new StateMachineRunner(config, evaluator);
    }

    @Test
    @DisplayName("ACQUIRING with GATEWAY only → NOT_LIVE / CAPTURED")
    void acquiring_gatewayOnly_notLiveCaptured() {
        GatewayEvent gw = new GatewayEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        StateResult result = runner.run(ctx, "ACQUIRING");
        assertThat(result.getBlockStatus()).isEqualTo("NOT_LIVE");
        assertThat(result.getTransactionStatus()).isEqualTo("CAPTURED");
    }

    @Test
    @DisplayName("ACQUIRING with SD → LIVE / SETTLED")
    void acquiring_withSd_liveSettled() {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        StateResult result = runner.run(ctx, "ACQUIRING");
        assertThat(result.getBlockStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactionStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("ACQUIRING with FIAPI (no SD) → LIVE / CAPTURED")
    void acquiring_withFiapi_liveCaptured() {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        StateResult result = runner.run(ctx, "ACQUIRING");
        assertThat(result.getBlockStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactionStatus()).isEqualTo("CAPTURED");
    }

    @Test
    @DisplayName("CASH → LIVE / SETTLED")
    void cash_liveSettled() {
        CashEvent cash = new CashEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);

        StateResult result = runner.run(ctx, "CASH");
        assertThat(result.getBlockStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactionStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("PAYOUT with no SD/CASH → LIVE / INITIATED")
    void payout_noSd_initiated() {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        StateResult result = runner.run(ctx, "PAYOUT");
        assertThat(result.getBlockStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactionStatus()).isEqualTo("INITIATED");
    }
}
