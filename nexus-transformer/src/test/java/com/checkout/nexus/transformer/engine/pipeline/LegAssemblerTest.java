package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.engine.resolver.*;
import com.checkout.nexus.transformer.model.Leg;
import com.checkout.nexus.transformer.model.le.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegAssemblerTest {

    private static NexusEngineConfig config;
    private static LegAssembler legAssembler;

    @BeforeAll
    static void setup() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                        new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        try (var in = LegAssemblerTest.class.getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }

        // Wire resolvers
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
        legAssembler = new LegAssembler(config, evaluator);
    }

    @Test
    @DisplayName("ACQUIRING+CAPTURE with SD: has SCHEME_SETTLEMENT leg with ACTUAL status")
    void acquiringCapture_withSd_hasSchemeSettlementLeg() throws Exception {
        SchemeSettlementEvent sd = buildSdEvent(300.0, "EUR");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        List<Leg> legs = legAssembler.assembleLegs(ctx, "ACQUIRING", "CAPTURE",
                "client-001", "CKO_ENTITY_001", "ACQ_ENTITY_001", "VISA");

        assertThat(legs).isNotEmpty();
        Leg ssLeg = legs.stream()
                .filter(l -> "SCHEME_SETTLEMENT".equals(l.getLegType()))
                .findFirst()
                .orElse(null);
        assertThat(ssLeg).isNotNull();
        assertThat(ssLeg.getLegStatus()).isEqualTo("ACTUAL");
        assertThat(ssLeg.getLegAmount()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("ACQUIRING+CAPTURE with COS only: SCHEME_SETTLEMENT leg with PREDICTED status")
    void acquiringCapture_withCosOnly_predictedStatus() throws Exception {
        CosEvent cos = buildCosEvent(50.0, "EUR", "FEE_TYPE_INTERCHANGE");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCosEvents(List.of(cos));
        LeContext ctx = new LeContext(tx);

        List<Leg> legs = legAssembler.assembleLegs(ctx, "ACQUIRING", "CAPTURE",
                "client-001", "CKO_ENTITY_001", "ACQ_ENTITY_001", "VISA");

        Leg ssLeg = legs.stream()
                .filter(l -> "SCHEME_SETTLEMENT".equals(l.getLegType()))
                .findFirst()
                .orElse(null);
        assertThat(ssLeg).isNotNull();
        assertThat(ssLeg.getLegStatus()).isEqualTo("PREDICTED");
    }

    @Test
    @DisplayName("ACQUIRING+AUTH with only GATEWAY: FUNDING leg PREDICTED")
    void acquiringAuth_gatewayOnly_fundingPredicted() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        AmountValue amount = new AmountValue();
        amount.setValue(200.0);
        amount.setCurrencyCode("GBP");
        setField(gw, "amount", amount);
        setField(gw, "eventType", "payment_approved");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        List<Leg> legs = legAssembler.assembleLegs(ctx, "ACQUIRING", "AUTH",
                "client-001", "CKO_ENTITY_001", "ACQ_ENTITY_001", "VISA");

        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).getLegType()).isEqualTo("FUNDING");
        assertThat(legs.get(0).getLegStatus()).isEqualTo("PREDICTED");
        assertThat(legs.get(0).getLegAmount()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("CASH+SETTLEMENT with CASH event: FUNDING leg ACTUAL")
    void cashSettlement_cashEvent_fundingActual() throws Exception {
        CashEvent cash = new CashEvent();
        CashEvent.StandardPayload payload = new CashEvent.StandardPayload();
        AmountValue amt = new AmountValue();
        amt.setValue(1000.0);
        amt.setCurrencyCode("EUR");
        setField(payload, "amount", amt);
        setField(payload, "direction", "credit");
        setField(cash, "standardPayload", payload);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);

        List<Leg> legs = legAssembler.assembleLegs(ctx, "CASH", "SETTLEMENT",
                "client-001", "CKO_ENTITY_001", "ACQ_ENTITY_001", "SWIFT");

        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).getLegType()).isEqualTo("FUNDING");
        assertThat(legs.get(0).getLegStatus()).isEqualTo("ACTUAL");
        assertThat(legs.get(0).getLegAmount()).isEqualTo(1000.0);
    }

    // ------------------------------------------------------------------ helpers

    private static SchemeSettlementEvent buildSdEvent(double amount, String currency) {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        SchemeSettlementEvent.Payload payload = new SchemeSettlementEvent.Payload();
        SchemeSettlementEvent.SignedAmount sa = new SchemeSettlementEvent.SignedAmount();
        AmountValue money = new AmountValue();
        money.setValue(amount);
        money.setCurrencyCode(currency);
        setField(sa, "money", money);
        setField(payload, "settlementAmount", sa);
        setField(payload, "fees", new ArrayList<>());
        setField(sd, "payload", payload);
        return sd;
    }

    private static CosEvent buildCosEvent(double feeValue, String currency, String feeType) {
        CosEvent cos = new CosEvent();
        CosEvent.Payload payload = new CosEvent.Payload();
        AmountValue fee = new AmountValue();
        fee.setValue(feeValue);
        fee.setCurrencyCode(currency);
        setField(payload, "fee", fee);
        setField(payload, "feeType", feeType);
        setField(cos, "payload", payload);
        return cos;
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
