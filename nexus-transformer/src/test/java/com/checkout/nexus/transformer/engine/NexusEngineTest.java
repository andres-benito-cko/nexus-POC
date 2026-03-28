package com.checkout.nexus.transformer.engine;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.engine.pipeline.*;
import com.checkout.nexus.transformer.engine.resolver.*;
import com.checkout.nexus.transformer.model.NexusBlock;
import com.checkout.nexus.transformer.model.le.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for NexusEngine — wires all components manually (no Spring context).
 */
class NexusEngineTest {

    private static NexusEngine engine;

    @BeforeAll
    static void setup() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                        new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        NexusEngineConfig config;
        try (var in = NexusEngineTest.class.getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }

        List<FieldResolver> resolvers = List.of(
                new GatewayAmountResolver(),
                new CashAmountResolver(),
                new SettlementAmountResolver(),
                new FundingAmountResolver(),
                new RollingReserveAmountResolver(),
                new SchemeFeesResolver(config),
                new FundingFeesResolver(config)
        );
        ResolverRegistry registry = new ResolverRegistry(resolvers);
        ExpressionEvaluator evaluator = new ExpressionEvaluator(registry);
        Classifier classifier = new Classifier(config, evaluator);
        StateMachineRunner smRunner = new StateMachineRunner(config, evaluator);
        LegAssembler legAssembler = new LegAssembler(config, evaluator);
        BlockAssembler assembler = new BlockAssembler(config, evaluator, legAssembler);
        engine = new NexusEngine(classifier, smRunner, assembler);
    }

    @Test
    @DisplayName("Gateway-only transaction → ACQUIRING/CAPTURE/NOT_LIVE with FUNDING leg")
    void gatewayOnly_acquiringCapture_notLive() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        setField(gw, "eventType", "payment_captured");
        setField(gw, "eventId", "gw-evt-001");
        AmountValue amount = new AmountValue();
        amount.setValue(150.0);
        amount.setCurrencyCode("GBP");
        setField(gw, "amount", amount);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setActionId("action-001");
        tx.setActionRootId("root-001");
        tx.setGatewayEvents(List.of(gw));

        LeContext ctx = new LeContext(tx);
        NexusBlock result = engine.transform(ctx);

        assertThat(result).isNotNull();
        assertThat(result.getNexusId()).isEqualTo("action-001");
        assertThat(result.getStatus()).isEqualTo("NOT_LIVE");
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getTransactions().get(0).getProductType()).isEqualTo("ACQUIRING");
        assertThat(result.getTransactions().get(0).getTransactionType()).isEqualTo("CAPTURE");
        assertThat(result.getTransactions().get(0).getTransactionStatus()).isEqualTo("CAPTURED");
        assertThat(result.getTransactions().get(0).getLegs()).isNotEmpty();
    }

    @Test
    @DisplayName("SD + GATEWAY transaction → ACQUIRING/CAPTURE/LIVE/SETTLED with SCHEME_SETTLEMENT leg")
    void withSd_acquiringCapture_settled() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        setField(gw, "eventType", "payment_captured");
        AmountValue gwAmount = new AmountValue();
        gwAmount.setValue(200.0);
        gwAmount.setCurrencyCode("EUR");
        setField(gw, "amount", gwAmount);

        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        SchemeSettlementEvent.Metadata sdMeta = new SchemeSettlementEvent.Metadata();
        setField(sdMeta, "scheme", "VISA");
        setField(sdMeta, "transactionType", "Capture");
        setField(sdMeta, "clientId", "client-999");
        setField(sdMeta, "settlementCompanyCkoLegalEntityCode", "CKO_UK");
        setField(sdMeta, "acquirerCompanyCkoLegalEntityCode", "CKO_ACQ");
        setField(sd, "metadata", sdMeta);
        SchemeSettlementEvent.Payload sdPayload = new SchemeSettlementEvent.Payload();
        SchemeSettlementEvent.SignedAmount sa = new SchemeSettlementEvent.SignedAmount();
        AmountValue money = new AmountValue();
        money.setValue(200.0);
        money.setCurrencyCode("EUR");
        setField(sa, "money", money);
        setField(sdPayload, "settlementAmount", sa);
        setField(sdPayload, "fees", List.of());
        setField(sd, "payload", sdPayload);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setActionId("action-002");
        tx.setActionRootId("root-002");
        tx.setGatewayEvents(List.of(gw));
        tx.setSchemeSettlementEvents(List.of(sd));

        LeContext ctx = new LeContext(tx);
        NexusBlock result = engine.transform(ctx);

        assertThat(result.getStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactions().get(0).getTransactionStatus()).isEqualTo("SETTLED");
        // Should have SCHEME_SETTLEMENT leg
        boolean hasSchemeSettlement = result.getTransactions().get(0).getLegs().stream()
                .anyMatch(l -> "SCHEME_SETTLEMENT".equals(l.getLegType()));
        assertThat(hasSchemeSettlement).isTrue();
    }

    @Test
    @DisplayName("CASH-only transaction → CASH/SETTLEMENT/LIVE")
    void cashOnly_cashSettlement_live() throws Exception {
        CashEvent cash = new CashEvent();
        CashEvent.StandardPayload payload = new CashEvent.StandardPayload();
        AmountValue amt = new AmountValue();
        amt.setValue(5000.0);
        amt.setCurrencyCode("EUR");
        setField(payload, "amount", amt);
        setField(payload, "direction", "credit");
        setField(cash, "standardPayload", payload);
        CashEvent.StandardMetadata sm = new CashEvent.StandardMetadata();
        setField(sm, "entityId", "client-cash-001");
        setField(sm, "scheme", "SWIFT");
        setField(sm, "actionType", "SETTLEMENT");
        setField(cash, "standardMetadata", sm);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setActionId("action-003");
        tx.setActionRootId("root-003");
        tx.setCashEvents(List.of(cash));

        LeContext ctx = new LeContext(tx);
        NexusBlock result = engine.transform(ctx);

        assertThat(result.getStatus()).isEqualTo("LIVE");
        assertThat(result.getTransactions().get(0).getProductType()).isEqualTo("CASH");
        assertThat(result.getTransactions().get(0).getTransactionType()).isEqualTo("SETTLEMENT");
        assertThat(result.getTransactions().get(0).getTransactionStatus()).isEqualTo("SETTLED");
    }

    // ------------------------------------------------------------------ helpers

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
