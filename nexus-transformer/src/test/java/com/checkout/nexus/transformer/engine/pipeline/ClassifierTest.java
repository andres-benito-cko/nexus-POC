package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.engine.resolver.ResolverRegistry;
import com.checkout.nexus.transformer.model.le.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClassifierTest {

    private static NexusEngineConfig config;
    private static Classifier classifier;

    @BeforeAll
    static void setup() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                        new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        try (var in = ClassifierTest.class.getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }
        ExpressionEvaluator evaluator = new ExpressionEvaluator(mock(ResolverRegistry.class));
        classifier = new Classifier(config, evaluator);
    }

    @Test
    @DisplayName("GATEWAY-only tx → ACQUIRING/CAPTURE")
    void gatewayOnly_acquiringCapture() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        setField(gw, "eventType", "payment_captured");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        ClassificationResult result = classifier.classify(ctx);
        assertThat(result.getProductType()).isEqualTo("ACQUIRING");
        assertThat(result.getTransactionType()).isEqualTo("CAPTURE");
    }

    @Test
    @DisplayName("FIAPI with CARD_PAYOUT actionType → PAYOUT/CREDIT")
    void fiapi_cardPayout_payoutCredit() throws Exception {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        BalancesChangedEvent.Metadata meta = new BalancesChangedEvent.Metadata();
        setField(meta, "actionType", "CARD_PAYOUT_CAPTURE");
        setField(fiapi, "metadata", meta);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        ClassificationResult result = classifier.classify(ctx);
        assertThat(result.getProductType()).isEqualTo("PAYOUT");
        assertThat(result.getTransactionType()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("CASH-only (no GATEWAY) → CASH/SETTLEMENT")
    void cashOnly_cashSettlement() {
        CashEvent cash = new CashEvent();
        CashEvent.StandardMetadata sm = new CashEvent.StandardMetadata();
        setField(sm, "actionType", "SETTLEMENT");
        setField(cash, "standardMetadata", sm);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);

        ClassificationResult result = classifier.classify(ctx);
        assertThat(result.getProductType()).isEqualTo("CASH");
        assertThat(result.getTransactionType()).isEqualTo("SETTLEMENT");
    }

    @Test
    @DisplayName("GATEWAY with payment_refunded → ACQUIRING/REFUND")
    void gateway_paymentRefunded_acquiringRefund() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        setField(gw, "eventType", "payment_refunded");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        ClassificationResult result = classifier.classify(ctx);
        assertThat(result.getProductType()).isEqualTo("ACQUIRING");
        assertThat(result.getTransactionType()).isEqualTo("REFUND");
    }

    @Test
    @DisplayName("SD with Capture transactionType → ACQUIRING/CAPTURE")
    void sd_captureTransactionType_acquiringCapture() throws Exception {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        SchemeSettlementEvent.Metadata meta = new SchemeSettlementEvent.Metadata();
        setField(meta, "transactionType", "Capture");
        setField(sd, "metadata", meta);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        ClassificationResult result = classifier.classify(ctx);
        assertThat(result.getProductType()).isEqualTo("ACQUIRING");
        assertThat(result.getTransactionType()).isEqualTo("CAPTURE");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
