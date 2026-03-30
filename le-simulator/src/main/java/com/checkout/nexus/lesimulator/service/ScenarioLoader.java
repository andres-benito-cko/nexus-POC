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
    private static final String TODAY     = LocalDate.now().toString();
    private static final String TOMORROW  = LocalDate.now().plusDays(1).toString();
    private static final String DAY_AFTER = LocalDate.now().plusDays(2).toString();
    private static final String NOW       = LocalDateTime.now().format(DT_FMT) + "Z";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<ScenarioInfo> listScenarios() {
        return List.of(
            new ScenarioInfo("01", "Acquiring Capture (Simple)",                      "Simple card capture, same-entity",               4),
            new ScenarioInfo("02", "Acquiring Capture (Cross-Currency)",               "Cross-currency capture GBP→EUR",                 4),
            new ScenarioInfo("03", "Acquiring Capture (Cross-Entity)",                 "Cross-entity capture with TRANSFER trade",       4),
            new ScenarioInfo("04", "Acquiring Capture (Cross-Currency+Cross-Entity)",  "Combined cross-currency and cross-entity",       4),
            new ScenarioInfo("05", "Acquiring Refund",                                 "Partial refund on a previous capture",           4),
            new ScenarioInfo("06", "Acquiring Chargeback",                             "Chargeback received from scheme",                4),
            new ScenarioInfo("07", "Payout",                                           "Card payout to client",                         4),
            new ScenarioInfo("08", "Top-up",                                           "Client top-up via bank transfer",               4),
            new ScenarioInfo("09", "Cash Matched Settlement",                          "Cash settlement matched to batch",               3)
        );
    }

    /** Loads a scenario using the default VISA scheme profile. */
    public List<LeLinkedTransaction> loadScenario(String scenarioId) {
        return loadScenario(scenarioId, SchemeProfile.VISA);
    }

    /** Loads a scenario using the supplied scheme profile. */
    public List<LeLinkedTransaction> loadScenario(String scenarioId, SchemeProfile scheme) {
        return switch (scenarioId) {
            case "01" -> buildAcquiringCaptureSimple(scheme);
            case "02" -> buildAcquiringCaptureCrossCurrency(scheme);
            case "03" -> buildAcquiringCaptureCrossEntity(scheme);
            case "04" -> buildAcquiringCaptureCrossCurrencyCrossEntity(scheme);
            case "05" -> buildAcquiringRefund(scheme);
            case "06" -> buildAcquiringChargeback(scheme);
            case "07" -> buildPayout(scheme);
            case "08" -> buildTopup(scheme);
            case "09" -> buildCashMatchedSettlement(scheme);
            default   -> throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
        };
    }

    /**
     * Builds a 4-version random capture sequence for the given scheme, amount, and client.
     * Pillar order: GW → COS → Balances → SD.
     */
    public List<LeLinkedTransaction> buildRandomCaptureSequence(SchemeProfile scheme, double amount, String clientId) {
        String actionId     = "act_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = scheme.defaultCurrency();

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * scheme.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true, scheme),
            cosEvent(env, r2(amount * scheme.schemeFeeRate()),      ccy, "SCHEME_FEE",      true, scheme)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, clientId, "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, r2(amount * 0.02), scheme)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            scheme, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", clientId,
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * scheme.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * scheme.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    // -------------------------------------------------------------------------
    // Scenario builders — GW → COS → Balances → SD
    // -------------------------------------------------------------------------

    private List<LeLinkedTransaction> buildAcquiringCaptureSimple(SchemeProfile s) {
        String actionId     = "act_cap_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 100.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      ccy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, r2(amount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrency(SchemeProfile s) {
        String actionId     = "act_cap_cc_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cc_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double processingAmount = 85.00;
        double holdingAmount    = 100.00;
        String processingCcy    = "GBP";
        String holdingCcy       = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, processingAmount, processingCcy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(processingAmount * s.interchangeFeeRate()), processingCcy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(processingAmount * s.schemeFeeRate()),      processingCcy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            holdingAmount, holdingCcy, processingAmount, processingCcy, TOMORROW, r2(holdingAmount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, processingAmount, processingCcy, processingAmount, processingCcy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", r2(processingAmount * s.interchangeFeeRate()), processingCcy),
                    sdFee("SCHEME_FEE",      r2(processingAmount * s.schemeFeeRate()),      processingCcy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossEntity(SchemeProfile s) {
        String actionId     = "act_cap_ce_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ce_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 200.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      ccy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, r2(amount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrencyCrossEntity(SchemeProfile s) {
        String actionId     = "act_cap_ccce_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ccce_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount        = 150.00;
        String processingCcy = "USD";
        String holdingCcy    = "EUR";
        double holdingAmount = 135.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, processingCcy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), processingCcy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      processingCcy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            holdingAmount, holdingCcy, amount, processingCcy, TOMORROW, r2(holdingAmount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, processingCcy, amount, processingCcy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), processingCcy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      processingCcy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringRefund(SchemeProfile s) {
        String actionId     = "act_ref_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 50.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_refunded", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.schemeFeeRate()), ccy, "SCHEME_FEE", true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_REFUND");
        bce.getMetadata().setSourceEventType("payment_refunded");
        bce.getMetadata().setEnhancedActionType("PartialRefund");
        v3.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Refund", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringChargeback(SchemeProfile s) {
        String actionId     = "act_cb_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"    + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 100.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_chargeback", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.schemeFeeRate()), ccy, "SCHEME_FEE", true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CHARGEBACK");
        bce.getMetadata().setSourceEventType("payment_chargeback");
        v3.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Chargeback", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("SCHEME_FEE", 25.00, ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildPayout(SchemeProfile s) {
        String actionId     = "act_po_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_po_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 500.00;
        String ccy          = "GBP";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_paid_out", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CARD_PAYOUT");
        bce.getMetadata().setSourceEventType("payment_paid_out");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, amount, ccy, "credit", "CKO_UK_LTD", "cli_acme_corp", TOMORROW)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "PayToCard", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildTopup(SchemeProfile s) {
        String actionId     = "act_tu_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_tu_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 1000.00;
        String ccy          = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_top_up", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TODAY, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_TOP_UP");
        bce.getMetadata().setSourceEventType("payment_top_up");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, amount, ccy, "debit", "CKO_UK_LTD", "cli_acme_corp", TODAY)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", TODAY, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildCashMatchedSettlement(SchemeProfile s) {
        String actionId     = "act_cash_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cash_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 5000.00;
        String ccy          = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .cashEvents(List.of(cashEvent(env, amount, ccy, "credit", "CKO_UK_LTD", "cli_acme_corp", TODAY)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TODAY, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CREDIT");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", TODAY, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3);
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

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

    private CosEvent cosEvent(EventEnvelope env, double feeAmount, String currency,
            String feeType, boolean predicted, SchemeProfile scheme) {
        return CosEvent.builder()
            .envelope(env)
            .payload(CosEvent.CosPayload.builder()
                .fee(AmountValue.builder().value(feeAmount).currencyCode(currency).build())
                .isPredicted(predicted)
                .feeType(feeType.equals("INTERCHANGE_FEE") ? "FEE_TYPE_INTERCHANGE" : "FEE_TYPE_SCHEME")
                .direction("increase")
                .build())
            .metadata(CosEvent.CosMetadata.builder()
                .acquirerName("CKO_UK_LTD")
                .acquirerCompanyCkoLegalEntityCode("CKO_UK_LTD")
                .paymentMethod(scheme.paymentMethodCos())
                .build())
            .build();
    }

    private BalancesChangedEvent balancesCapture(EventEnvelope env, String clientId,
            String settlementEntity, String acquirerEntity,
            double holdingAmount, String holdingCcy,
            double processingAmount, String processingCcy,
            String valueDate, double reserveAmount, SchemeProfile scheme) {
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
                .schemeCode(scheme.schemeName().toUpperCase().replace(" ", "_"))
                .paymentMethod(scheme.paymentMethodBalances())
                .sourceEventType("payment_captured")
                .actionType("BALANCES_CHANGED_ACTION_TYPE_CAPTURE")
                .currencyAccountId("ca_" + clientId + "_" + holdingCcy.toLowerCase() + "_001")
                .acquirerName(acquirerEntity)
                .acquirerCountry(scheme.acquirerCountry())
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

    private SchemeSettlementEvent sdEvent(EventEnvelope env,
            double settlementAmount, String settlementCcy,
            double processingAmount, String processingCcy,
            SchemeProfile scheme, String txnType, String valueDate,
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
                .scheme(scheme.schemeName())
                .transactionType(txnType)
                .expectedValueDate(valueDate)
                .centralProcessingDate(TODAY)
                .settlementServiceName(scheme.settlementServiceName())
                .settlementCountryCode(scheme.acquirerCountry())
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .settlementCompanyCkoLegalEntityCode(settlementEntity)
                .acquirerName(acquirerEntity)
                .acquirerCountry(scheme.acquirerCountry())
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

    private static double r2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // -------------------------------------------------------------------------
    // Legacy single-transaction helpers (used by SimulatorService random mode)
    // -------------------------------------------------------------------------

    public LeLinkedTransaction randomCapture() {
        Random rnd = new Random();
        double amount = Math.round((10 + rnd.nextDouble() * 990) * 100.0) / 100.0;
        SchemeProfile[] profiles = {SchemeProfile.VISA, SchemeProfile.MASTERCARD, SchemeProfile.AMEX};
        SchemeProfile scheme = profiles[rnd.nextInt(profiles.length)];
        List<LeLinkedTransaction> versions = buildRandomCaptureSequence(scheme, amount, "cli_acme_corp");
        int version = rnd.nextInt(versions.size());
        return versions.get(version);
    }

    public LeLinkedTransaction randomRefund() {
        String actionId     = "act_rnd_ref_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_rnd_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        Random rnd          = new Random();
        double amount       = Math.round((5 + rnd.nextDouble() * 200) * 100.0) / 100.0;
        String ccy          = "EUR";

        LeLinkedTransaction txn = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId)
            .actionRootId(actionRootId)
            .transactionVersion(rnd.nextInt(3) + 2)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_refunded", amount, ccy, actionRootId)))
            .build();

        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, SchemeProfile.VISA);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_REFUND");
        bce.getMetadata().setSourceEventType("payment_refunded");
        txn.setBalancesChangedEvents(List.of(bce));

        return txn;
    }
}
