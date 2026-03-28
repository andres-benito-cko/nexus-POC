package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.Fee;
import com.checkout.nexus.transformer.model.le.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResolversTest {

    private static NexusEngineConfig config;

    @BeforeAll
    static void loadConfig() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                        new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        try (var in = ResolversTest.class.getClassLoader().getResourceAsStream("nexus-engine-config.yaml")) {
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }
    }

    // ------------------------------------------------------------------ GATEWAY_AMOUNT

    @Test
    @DisplayName("GatewayAmountResolver returns gateway amount")
    void gatewayAmount_returnsGatewayAmount() {
        GatewayEvent gw = new GatewayEvent();
        AmountValue amount = new AmountValue();
        amount.setValue(100.50);
        amount.setCurrencyCode("GBP");
        setField(gw, "amount", amount);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        GatewayAmountResolver resolver = new GatewayAmountResolver();
        Object result = resolver.resolve(ctx, null);

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(100.50);
        assertThat(((AmountValue) result).getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("GatewayAmountResolver returns null when GATEWAY absent")
    void gatewayAmount_returnsNullWhenAbsent() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        GatewayAmountResolver resolver = new GatewayAmountResolver();
        assertThat(resolver.resolve(ctx, null)).isNull();
    }

    // ------------------------------------------------------------------ CASH_AMOUNT

    @Test
    @DisplayName("CashAmountResolver returns cash standard payload amount")
    void cashAmount_returnsCashAmount() {
        CashEvent cash = new CashEvent();
        CashEvent.StandardPayload payload = new CashEvent.StandardPayload();
        AmountValue amt = new AmountValue();
        amt.setValue(500.00);
        amt.setCurrencyCode("EUR");
        setField(payload, "amount", amt);
        setField(cash, "standardPayload", payload);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);

        CashAmountResolver resolver = new CashAmountResolver();
        Object result = resolver.resolve(ctx, null);

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(500.00);
    }

    // ------------------------------------------------------------------ SETTLEMENT_AMOUNT

    @Test
    @DisplayName("SettlementAmountResolver returns SD settlement amount when SD present")
    void settlementAmount_fromSd() {
        SchemeSettlementEvent sd = buildSdEvent(250.75, "USD");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        SettlementAmountResolver resolver = new SettlementAmountResolver();
        Object result = resolver.resolve(ctx, Map.of("source_priority", List.of("SD", "COS")));

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(250.75);
        assertThat(((AmountValue) result).getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("SettlementAmountResolver falls back to COS fee amount when no SD")
    void settlementAmount_fromCos() {
        CosEvent cos = buildCosEvent(75.00, "EUR", "FEE_TYPE_INTERCHANGE");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCosEvents(List.of(cos));
        LeContext ctx = new LeContext(tx);

        SettlementAmountResolver resolver = new SettlementAmountResolver();
        Object result = resolver.resolve(ctx, Map.of("source_priority", List.of("SD", "COS")));

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(75.00);
    }

    @Test
    @DisplayName("SettlementAmountResolver returns null when neither SD nor COS present")
    void settlementAmount_returnsNullWhenAbsent() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        SettlementAmountResolver resolver = new SettlementAmountResolver();
        assertThat(resolver.resolve(ctx, null)).isNull();
    }

    // ------------------------------------------------------------------ SCHEME_FEES

    @Test
    @DisplayName("SchemeFeesResolver maps SD fees to Fee objects with ACTUAL status")
    void schemeFees_fromSd_mapsToActualFees() {
        SchemeSettlementEvent sd = buildSdEvent(100.0, "EUR");
        SchemeSettlementEvent.SdFee sdFee = new SchemeSettlementEvent.SdFee();
        setField(sdFee, "type", "INTERCHANGE_FEE");
        setField(sdFee, "roundedAmount", 2.50);
        setField(sdFee, "currencyCode", "EUR");
        SchemeSettlementEvent.Payload payload = new SchemeSettlementEvent.Payload();
        setField(payload, "fees", List.of(sdFee));
        setField(sd, "payload", payload);
        // also set settlementAmount
        SchemeSettlementEvent.SignedAmount sa = new SchemeSettlementEvent.SignedAmount();
        AmountValue money = new AmountValue();
        money.setValue(100.0);
        money.setCurrencyCode("EUR");
        setField(sa, "money", money);
        setField(payload, "settlementAmount", sa);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        SchemeFeesResolver resolver = new SchemeFeesResolver(config);
        @SuppressWarnings("unchecked")
        List<Fee> fees = (List<Fee>) resolver.resolve(ctx, Map.of("source_priority", List.of("SD", "COS")));

        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getFeeType()).isEqualTo("INTERCHANGE");
        assertThat(fees.get(0).getFeeStatus()).isEqualTo("ACTUAL");
        assertThat(fees.get(0).getFeeAmount()).isEqualTo(2.50);
    }

    @Test
    @DisplayName("SchemeFeesResolver maps COS fees to Fee objects with PREDICTED status")
    void schemeFees_fromCos_mapsToFees() {
        CosEvent cos = buildCosEvent(10.0, "EUR", "FEE_TYPE_SCHEME");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setCosEvents(List.of(cos));
        LeContext ctx = new LeContext(tx);

        SchemeFeesResolver resolver = new SchemeFeesResolver(config);
        @SuppressWarnings("unchecked")
        List<Fee> fees = (List<Fee>) resolver.resolve(ctx, Map.of("source_priority", List.of("SD", "COS")));

        assertThat(fees).isNotEmpty();
        assertThat(fees.get(0).getFeeType()).isEqualTo("SCHEME_FEE");
        assertThat(fees.get(0).getFeeStatus()).isEqualTo("PREDICTED");
    }

    // ------------------------------------------------------------------ FUNDING_AMOUNT

    @Test
    @DisplayName("FundingAmountResolver returns holding amount from first FIAPI action")
    void fundingAmount_returnsHoldingAmount() {
        BalancesChangedEvent fiapi = buildFiapiEvent(300.0, "GBP");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        FundingAmountResolver resolver = new FundingAmountResolver();
        Object result = resolver.resolve(ctx, null);

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(300.0);
        assertThat(((AmountValue) result).getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("FundingAmountResolver returns null when FIAPI absent")
    void fundingAmount_returnsNullWhenAbsent() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);
        FundingAmountResolver resolver = new FundingAmountResolver();
        assertThat(resolver.resolve(ctx, null)).isNull();
    }

    // ------------------------------------------------------------------ ROLLING_RESERVE_AMOUNT

    @Test
    @DisplayName("RollingReserveAmountResolver returns rolling reserve when > 0")
    void rollingReserve_returnsAmount() {
        BalancesChangedEvent fiapi = buildFiapiEvent(300.0, "GBP");
        // Add rolling reserve
        BalancesChangedEvent.Changes changes = fiapi.getActions().get(0).getChanges();
        AmountValue reserve = new AmountValue();
        reserve.setValue(30.0);
        reserve.setCurrencyCode("GBP");
        setField(changes, "rollingReserve", reserve);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        RollingReserveAmountResolver resolver = new RollingReserveAmountResolver();
        Object result = resolver.resolve(ctx, null);

        assertThat(result).isInstanceOf(AmountValue.class);
        assertThat(((AmountValue) result).getValue()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("RollingReserveAmountResolver returns null when rolling reserve is zero")
    void rollingReserve_returnsNullWhenZero() {
        BalancesChangedEvent fiapi = buildFiapiEvent(300.0, "GBP");
        // rollingReserve with value 0
        BalancesChangedEvent.Changes changes = fiapi.getActions().get(0).getChanges();
        AmountValue reserve = new AmountValue();
        reserve.setValue(0.0);
        reserve.setCurrencyCode("GBP");
        setField(changes, "rollingReserve", reserve);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        RollingReserveAmountResolver resolver = new RollingReserveAmountResolver();
        assertThat(resolver.resolve(ctx, null)).isNull();
    }

    // ------------------------------------------------------------------ FUNDING_FEES

    @Test
    @DisplayName("FundingFeesResolver maps FIAPI fee actions to Fee objects")
    void fundingFees_mapsActions() {
        BalancesChangedEvent fiapi = buildFiapiEventWithFeeAction("Gateway", 150.0, "EUR");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        FundingFeesResolver resolver = new FundingFeesResolver(config);
        @SuppressWarnings("unchecked")
        List<Fee> fees = (List<Fee>) resolver.resolve(ctx, null);

        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getFeeType()).isEqualTo("PROCESSING_FEE");
        assertThat(fees.get(0).getFeeStatus()).isEqualTo("ACTUAL");
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

    private static BalancesChangedEvent buildFiapiEvent(double holdingAmount, String currency) {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        BalancesChangedEvent.Action action = new BalancesChangedEvent.Action();
        BalancesChangedEvent.Changes changes = new BalancesChangedEvent.Changes();
        BalancesChangedEvent.PendingChange pending = new BalancesChangedEvent.PendingChange();
        AmountValue holding = new AmountValue();
        holding.setValue(holdingAmount);
        holding.setCurrencyCode(currency);
        setField(pending, "holdingAmount", holding);
        setField(changes, "pending", pending);
        setField(action, "changes", changes);
        fiapi.setActions(List.of(action));
        return fiapi;
    }

    private static BalancesChangedEvent buildFiapiEventWithFeeAction(String feeType, double holdingAmount, String currency) {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        BalancesChangedEvent.Action action = new BalancesChangedEvent.Action();
        BalancesChangedEvent.ActionMetadata meta = new BalancesChangedEvent.ActionMetadata();
        setField(meta, "amountType", "fee");
        setField(meta, "feeType", feeType);
        setField(action, "actionMetadata", meta);
        BalancesChangedEvent.Changes changes = new BalancesChangedEvent.Changes();
        BalancesChangedEvent.PendingChange pending = new BalancesChangedEvent.PendingChange();
        AmountValue holding = new AmountValue();
        holding.setValue(holdingAmount);
        holding.setCurrencyCode(currency);
        setField(pending, "holdingAmount", holding);
        setField(changes, "pending", pending);
        setField(action, "changes", changes);
        fiapi.setActions(List.of(action));
        return fiapi;
    }

    static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}
