package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScenarioLoader {

    public record ScenarioInfo(String id, String name, String description, int versionCount) {}

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_DATE_TIME;
    private static final String TODAY = LocalDate.now().toString();
    private static final String TOMORROW = LocalDate.now().plusDays(1).toString();
    private static final String DAY_AFTER = LocalDate.now().plusDays(2).toString();
    private static final String NOW = LocalDateTime.now().format(DT_FMT) + "Z";

    public List<ScenarioInfo> listScenarios() {
        return List.of(
            new ScenarioInfo("01", "Acquiring Capture (Simple)", "Simple card capture EUR same-entity", 4),
            new ScenarioInfo("02", "Acquiring Capture (Cross-Currency)", "Cross-currency capture GBP->EUR", 4),
            new ScenarioInfo("03", "Acquiring Capture (Cross-Entity)", "Cross-entity capture with TRANSFER trade", 4),
            new ScenarioInfo("04", "Acquiring Capture (Cross-Currency + Cross-Entity)", "Combined cross-currency and cross-entity", 4),
            new ScenarioInfo("05", "Acquiring Refund", "Partial refund on a previous capture", 4),
            new ScenarioInfo("06", "Acquiring Chargeback", "Chargeback received from scheme", 3),
            new ScenarioInfo("07", "Payout", "Card payout to client", 3),
            new ScenarioInfo("08", "Top-up", "Client top-up via bank transfer", 3),
            new ScenarioInfo("09", "Cash Matched Settlement", "Cash settlement matched to batch", 3)
        );
    }

    /**
     * Generates a list of LE linked transaction versions for a scenario.
     * Each version simulates incremental pillar arrival.
     */
    public List<LeLinkedTransaction> loadScenario(String scenarioId) {
        return switch (scenarioId) {
            case "01" -> buildAcquiringCaptureSimple();
            case "02" -> buildAcquiringCaptureCrossCurrency();
            case "03" -> buildAcquiringCaptureCrossEntity();
            case "04" -> buildAcquiringCaptureCrossCurrencyCrossEntity();
            case "05" -> buildAcquiringRefund();
            case "06" -> buildAcquiringChargeback();
            case "07" -> buildPayout();
            case "08" -> buildTopup();
            case "09" -> buildCashMatchedSettlement();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
        };
    }

    // --- Scenario 01: Simple Acquiring Capture ---

    private List<LeLinkedTransaction> buildAcquiringCaptureSimple() {
        String actionId = "act_cap_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_" + UUID.randomUUID().toString().substring(0, 8);

        EventEnvelope env = envelope(actionId, actionRootId);

        // V1: Gateway only
        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, 100.00, "EUR", actionRootId)))
            .build();

        // V2: Gateway + FIAPI (Balances)
        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            100.00, "EUR", 100.00, "EUR", TOMORROW, 2.00)));

        // V3: Gateway + FIAPI + COS
        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCosEvents(List.of(
            cosEvent(env, 0.50, "EUR", "INTERCHANGE_FEE", true, "CKO_UK_LTD"),
            cosEvent(env, 0.10, "EUR", "SCHEME_FEE", true, "CKO_UK_LTD")
        ));

        // V4: All pillars + SD (SETTLED)
        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdCapture(env, 100.00, "EUR", 100.00, "EUR",
            "VISA", "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", 0.48, "EUR"), sdFee("SCHEME_FEE", 0.09, "EUR")))));

        return List.of(v1, v2, v3, v4);
    }

    // --- Scenario 02: Cross-Currency Capture ---

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrency() {
        String actionId = "act_cap_cc_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cc_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, 85.00, "GBP", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            100.00, "EUR", 85.00, "GBP", TOMORROW, 2.00)));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCosEvents(List.of(
            cosEvent(env, 0.45, "GBP", "INTERCHANGE_FEE", true, "CKO_UK_LTD"),
            cosEvent(env, 0.08, "GBP", "SCHEME_FEE", true, "CKO_UK_LTD")
        ));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdCapture(env, 85.00, "GBP", 85.00, "GBP",
            "VISA", "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", 0.43, "GBP"), sdFee("SCHEME_FEE", 0.07, "GBP")))));

        return List.of(v1, v2, v3, v4);
    }

    // --- Scenario 03: Cross-Entity Capture ---

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossEntity() {
        String actionId = "act_cap_ce_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ce_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, 200.00, "EUR", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            200.00, "EUR", 200.00, "EUR", TOMORROW, 4.00)));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCosEvents(List.of(
            cosEvent(env, 1.00, "EUR", "INTERCHANGE_FEE", true, "CKO_UK_LTD"),
            cosEvent(env, 0.20, "EUR", "SCHEME_FEE", true, "CKO_UK_LTD")
        ));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdCapture(env, 200.00, "EUR", 200.00, "EUR",
            "MC", "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", 0.95, "EUR"), sdFee("SCHEME_FEE", 0.18, "EUR")))));

        return List.of(v1, v2, v3, v4);
    }

    // --- Scenario 04: Cross-Currency + Cross-Entity ---

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrencyCrossEntity() {
        String actionId = "act_cap_ccce_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ccce_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, 150.00, "USD", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            135.00, "EUR", 150.00, "USD", TOMORROW, 2.70)));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCosEvents(List.of(
            cosEvent(env, 0.75, "USD", "INTERCHANGE_FEE", true, "CKO_UK_LTD"),
            cosEvent(env, 0.15, "USD", "SCHEME_FEE", true, "CKO_UK_LTD")
        ));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdCapture(env, 150.00, "USD", 150.00, "USD",
            "VISA", "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", 0.72, "USD"), sdFee("SCHEME_FEE", 0.14, "USD")))));

        return List.of(v1, v2, v3, v4);
    }

    // --- Scenario 05: Acquiring Refund ---

    private List<LeLinkedTransaction> buildAcquiringRefund() {
        String actionId = "act_ref_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_refunded", 50.00, "EUR", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            50.00, "EUR", 50.00, "EUR", TOMORROW, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_REFUND");
        bce.getMetadata().setSourceEventType("payment_refunded");
        bce.getMetadata().setEnhancedActionType("PartialRefund");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCosEvents(List.of(
            cosEvent(env, 0.10, "EUR", "SCHEME_FEE", true, "CKO_UK_LTD")
        ));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, 50.00, "EUR", 50.00, "EUR",
            "VISA", "Refund", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    // --- Scenario 06: Acquiring Chargeback ---

    private List<LeLinkedTransaction> buildAcquiringChargeback() {
        String actionId = "act_cb_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_chargeback", 100.00, "EUR", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            100.00, "EUR", 100.00, "EUR", TOMORROW, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CHARGEBACK");
        bce.getMetadata().setSourceEventType("payment_chargeback");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setSchemeSettlementEvents(List.of(sdEvent(env, 100.00, "EUR", 100.00, "EUR",
            "VISA", "Chargeback", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("SCHEME_FEE", 25.00, "EUR")))));

        return List.of(v1, v2, v3);
    }

    // --- Scenario 07: Payout ---

    private List<LeLinkedTransaction> buildPayout() {
        String actionId = "act_po_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_po_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_paid_out", 500.00, "GBP", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            500.00, "GBP", 500.00, "GBP", TOMORROW, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CARD_PAYOUT");
        bce.getMetadata().setSourceEventType("payment_paid_out");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, 500.00, "GBP", "credit",
            "CKO_UK_LTD", "cli_acme_corp", TOMORROW)));

        return List.of(v1, v2, v3);
    }

    // --- Scenario 08: Top-up ---

    private List<LeLinkedTransaction> buildTopup() {
        String actionId = "act_tu_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_tu_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_top_up", 1000.00, "EUR", actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            1000.00, "EUR", 1000.00, "EUR", TODAY, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_TOP_UP");
        bce.getMetadata().setSourceEventType("payment_top_up");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, 1000.00, "EUR", "debit",
            "CKO_UK_LTD", "cli_acme_corp", TODAY)));

        return List.of(v1, v2, v3);
    }

    // --- Scenario 09: Cash Matched Settlement ---

    private List<LeLinkedTransaction> buildCashMatchedSettlement() {
        String actionId = "act_cash_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cash_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        // Pure CASH-pillar settlement — no gateway events.
        // The family classifier rule requires !GATEWAY && CASH to classify as CASH family.
        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(1)
            .cashEvents(List.of(cashEvent(env, 5000.00, "EUR", "credit",
                "CKO_UK_LTD", "cli_acme_corp", TODAY)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            5000.00, "EUR", 5000.00, "EUR", TODAY, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CREDIT");
        v2.setBalancesChangedEvents(List.of(bce));

        return List.of(v1, v2);
    }

    // --- Helper builders ---

    private EventEnvelope envelope(String actionId, String actionRootId) {
        return EventEnvelope.builder()
            .actionId(actionId)
            .actionRootId(actionRootId)
            .originUniqueEventId(UUID.randomUUID().toString())
            .originEventTimestamp(NOW)
            .correctorVersion(0)
            .processedCount(0)
            .build();
    }

    private GatewayEvent gatewayCapture(EventEnvelope env, double amount, String currency, String paymentId) {
        return gatewayEvent(env, "payment_captured", amount, currency, paymentId);
    }

    private GatewayEvent gatewayEvent(EventEnvelope env, String eventType, double amount, String currency, String paymentId) {
        return GatewayEvent.builder()
            .envelope(env)
            .paymentId(paymentId)
            .eventType(eventType)
            .processedOn(NOW)
            .amount(AmountValue.builder().value(amount).currencyCode(currency).build())
            .acquirerName("CKO_UK_LTD")
            .acquirerCountry("GB")
            .eventId(UUID.randomUUID().toString())
            .responseCode("10000")
            .build();
    }

    private BalancesChangedEvent balancesCapture(EventEnvelope env, String clientId,
            String settlementEntity, String acquirerEntity,
            double holdingAmount, String holdingCcy,
            double processingAmount, String processingCcy,
            String valueDate, double reserveAmount) {
        BalancesChangedEvent.BalancesChanges changes = BalancesChangedEvent.BalancesChanges.builder()
            .pending(BalancesChangedEvent.PendingChange.builder()
                .holdingAmount(AmountValue.builder().value(holdingAmount).currencyCode(holdingCcy).build())
                .processingAmount(AmountValue.builder().value(processingAmount).currencyCode(processingCcy).build())
                .build())
            .build();
        if (reserveAmount > 0) {
            changes.setRollingReserve(AmountValue.builder().value(reserveAmount).currencyCode(holdingCcy).build());
        }

        return BalancesChangedEvent.builder()
            .envelope(env)
            .metadata(BalancesChangedEvent.BalancesMetadata.builder()
                .clientId(clientId)
                .entityId(clientId)
                .settlementCompanyCkoLegalEntityCode(settlementEntity)
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .schemeCode("VISA")
                .paymentMethod("card")
                .sourceEventType("payment_captured")
                .actionType("BALANCES_CHANGED_ACTION_TYPE_CAPTURE")
                .currencyAccountId("ca_" + clientId + "_" + holdingCcy.toLowerCase() + "_001")
                .acquirerName(acquirerEntity)
                .acquirerCountry("GB")
                .clientSettlementType("Net")
                .valueDate(valueDate)
                .build())
            .actions(List.of(BalancesChangedEvent.BalancesAction.builder()
                .changes(changes)
                .actionMetadata(BalancesChangedEvent.ActionMetadata.builder()
                    .amountType("revenue")
                    .build())
                .build()))
            .build();
    }

    private CosEvent cosEvent(EventEnvelope env, double feeAmount, String currency,
            String feeType, boolean predicted, String acquirerEntity) {
        return CosEvent.builder()
            .envelope(env)
            .payload(CosEvent.CosPayload.builder()
                .fee(AmountValue.builder().value(feeAmount).currencyCode(currency).build())
                .isPredicted(predicted)
                .feeType(feeType.equals("INTERCHANGE_FEE") ? "FEE_TYPE_INTERCHANGE" : "FEE_TYPE_SCHEME")
                .direction("increase")
                .build())
            .metadata(CosEvent.CosMetadata.builder()
                .acquirerName(acquirerEntity)
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .paymentMethod("CARD")
                .build())
            .build();
    }

    private SchemeSettlementEvent sdCapture(EventEnvelope env, double settlementAmount, String settlementCcy,
            double processingAmount, String processingCcy,
            String scheme, String txnType, String valueDate,
            String acquirerEntity, String settlementEntity, String clientId,
            List<SchemeSettlementEvent.SdFee> fees) {
        return sdEvent(env, settlementAmount, settlementCcy, processingAmount, processingCcy,
            scheme, txnType, valueDate, acquirerEntity, settlementEntity, clientId, fees);
    }

    private SchemeSettlementEvent sdEvent(EventEnvelope env, double settlementAmount, String settlementCcy,
            double processingAmount, String processingCcy,
            String scheme, String txnType, String valueDate,
            String acquirerEntity, String settlementEntity, String clientId,
            List<SchemeSettlementEvent.SdFee> fees) {
        return SchemeSettlementEvent.builder()
            .envelope(env)
            .payload(SchemeSettlementEvent.SdPayload.builder()
                .settlementAmount(SchemeSettlementEvent.SignedAmount.builder()
                    .money(AmountValue.builder().value(settlementAmount).currencyCode(settlementCcy).build())
                    .sign("positive")
                    .build())
                .processingAmount(SchemeSettlementEvent.SignedAmount.builder()
                    .money(AmountValue.builder().value(processingAmount).currencyCode(processingCcy).build())
                    .sign("positive")
                    .build())
                .fees(fees)
                .build())
            .metadata(SchemeSettlementEvent.SdMetadata.builder()
                .scheme(scheme)
                .transactionType(txnType)
                .expectedValueDate(valueDate)
                .centralProcessingDate(TODAY)
                .settlementServiceName("ISS")
                .settlementCountryCode("GB")
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .settlementCompanyCkoLegalEntityCode(settlementEntity)
                .acquirerName(acquirerEntity)
                .acquirerCountry("GB")
                .entityId(clientId)
                .clientId(clientId)
                .build())
            .build();
    }

    private SchemeSettlementEvent.SdFee sdFee(String type, double amount, String currency) {
        return SchemeSettlementEvent.SdFee.builder()
            .type(type)
            .roundedAmount(amount)
            .currencyCode(currency)
            .sign("positive")
            .taxAmount(0)
            .build();
    }

    private CashEvent cashEvent(EventEnvelope env, double amount, String currency,
            String direction, String legalEntity, String entityId, String valueDate) {
        return CashEvent.builder()
            .envelope(env)
            .standardPayload(CashEvent.CashStandardPayload.builder()
                .amount(AmountValue.builder().value(amount).currencyCode(currency).build())
                .direction(direction)
                .build())
            .standardMetadata(CashEvent.CashStandardMetadata.builder()
                .scheme("VISA")
                .valueDate(valueDate)
                .legalEntity(legalEntity)
                .actionType("SETTLEMENT")
                .entityId(entityId)
                .currencyAccountId("ca_" + entityId + "_" + currency.toLowerCase() + "_001")
                .cashBatchId("batch_" + UUID.randomUUID().toString().substring(0, 8))
                .build())
            .build();
    }

    private LeLinkedTransaction copyWithVersion(LeLinkedTransaction src, int version) {
        return LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(src.getActionId())
            .actionRootId(src.getActionRootId())
            .transactionVersion(version)
            .gatewayEvents(new ArrayList<>(src.getGatewayEvents()))
            .balancesChangedEvents(new ArrayList<>(src.getBalancesChangedEvents()))
            .cosEvents(new ArrayList<>(src.getCosEvents()))
            .schemeSettlementEvents(new ArrayList<>(src.getSchemeSettlementEvents()))
            .cashEvents(new ArrayList<>(src.getCashEvents()))
            .build();
    }

    // --- Random generation helpers ---

    public LeLinkedTransaction randomCapture() {
        String actionId = "act_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        Random rnd = new Random();
        double amount = Math.round((10 + rnd.nextDouble() * 990) * 100.0) / 100.0;
        String[] currencies = {"EUR", "GBP", "USD"};
        String ccy = currencies[rnd.nextInt(currencies.length)];
        String[] schemes = {"VISA", "MC", "AMEX"};
        String scheme = schemes[rnd.nextInt(schemes.length)];

        LeLinkedTransaction txn = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(rnd.nextInt(4) + 1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .balancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
                amount, ccy, amount, ccy, TOMORROW, Math.round(amount * 0.02 * 100.0) / 100.0)))
            .cosEvents(List.of(
                cosEvent(env, Math.round(amount * 0.005 * 100.0) / 100.0, ccy, "INTERCHANGE_FEE", true, "CKO_UK_LTD"),
                cosEvent(env, Math.round(amount * 0.001 * 100.0) / 100.0, ccy, "SCHEME_FEE", true, "CKO_UK_LTD")
            ))
            .build();

        if (txn.getTransactionVersion() >= 4) {
            txn.setSchemeSettlementEvents(List.of(sdCapture(env, amount, ccy, amount, ccy,
                scheme, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
                List.of(sdFee("INTERCHANGE_FEE", Math.round(amount * 0.0048 * 100.0) / 100.0, ccy),
                    sdFee("SCHEME_FEE", Math.round(amount * 0.0009 * 100.0) / 100.0, ccy)))));
        }

        return txn;
    }

    public LeLinkedTransaction randomRefund() {
        String actionId = "act_rnd_ref_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env = envelope(actionId, actionRootId);

        Random rnd = new Random();
        double amount = Math.round((5 + rnd.nextDouble() * 200) * 100.0) / 100.0;
        String ccy = "EUR";

        LeLinkedTransaction txn = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(rnd.nextInt(3) + 2)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_refunded", amount, ccy, actionRootId)))
            .build();

        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_REFUND");
        bce.getMetadata().setSourceEventType("payment_refunded");
        txn.setBalancesChangedEvents(List.of(bce));

        return txn;
    }
}
