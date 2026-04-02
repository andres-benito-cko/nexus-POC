package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.FieldMapping;
import com.checkout.nexus.transformer.engine.config.ConfigHolder;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.model.*;
import com.checkout.nexus.transformer.model.le.AmountValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Assembles a complete {@link NexusBlock} from a classified and state-resolved context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockAssembler {

    private final ConfigHolder config;
    private final ExpressionEvaluator evaluator;
    private final LegAssembler legAssembler;

    public NexusBlock assemble(LeContext ctx, ClassificationResult classification, StateResult state) {
        Map<String, FieldMapping> fieldMappings = config.getFieldMappings();

        // Resolve core entity fields
        String entityId = resolveField("entity_id", fieldMappings, ctx, "unknown_entity");
        String ckoEntityId = resolveField("cko_entity_id", fieldMappings, ctx, "CKO_UNKNOWN");
        String acquirerEntity = resolveField("acquirer_entity", fieldMappings, ctx, "CKO_UNKNOWN");
        String scheme = resolveField("scheme", fieldMappings, ctx, "UNKNOWN_SCHEME");
        String processedAt = resolveField("processed_at", fieldMappings, ctx, null);

        // Build trade amount/currency from gateway or first resolved leg
        double transactionAmount = 0.0;
        String transactionCurrency = "EUR";
        String transactionDate = LocalDate.now().toString();

        AmountValue gatewayAmt = resolveAmountValue("GATEWAY.amount", ctx);
        if (gatewayAmt != null) {
            transactionAmount = gatewayAmt.getValue();
            transactionCurrency = gatewayAmt.getCurrencyCode();
        }

        Object gwProcessedOn = ctx.get("GATEWAY.processedOn");
        if (gwProcessedOn != null) {
            String gwDate = gwProcessedOn.toString();
            transactionDate = gwDate.length() >= 10 ? gwDate.substring(0, 10) : gwDate;
        }

        // Build trade metadata
        TransactionMetadata metadata = buildMetadata(fieldMappings, ctx);

        // Assemble legs
        List<Leg> legs = legAssembler.assembleLegs(ctx,
                classification.getProductType(),
                classification.getTransactionType(),
                entityId, ckoEntityId, acquirerEntity, scheme);

        // If no legs came out of the config, generate a default FUNDING leg
        if (legs.isEmpty()) {
            legs = List.of(buildDefaultLeg(ctx, transactionAmount, transactionCurrency, entityId, ckoEntityId));
        }

        // Build trade
        String transactionId = ctx.getRaw().getActionId() + "_1";
        // Re-apply proper leg IDs based on transactionId
        legs = reapplyLegIds(legs, transactionId);

        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .productType(classification.getProductType())
                .transactionType(classification.getTransactionType())
                .transactionStatus(state.getTransactionStatus())
                .transactionAmount(transactionAmount)
                .transactionCurrency(transactionCurrency)
                .transactionDate(transactionDate)
                .metadata(metadata)
                .legs(legs)
                .build();

        return NexusBlock.builder()
                .nexusId(ctx.getRaw().getActionId())
                .parentNexusId(ctx.getRaw().getActionRootId())
                .actionId(ctx.getRaw().getActionId())
                .actionRootId(ctx.getRaw().getActionRootId())
                .status(state.getBlockStatus())
                .entity(Entity.builder().id(entityId).build())
                .ckoEntityId(ckoEntityId)
                .processedAt(processedAt)
                .transactions(List.of(transaction))
                .build();
    }

    // ------------------------------------------------------------------ private helpers

    private String resolveField(String key, Map<String, FieldMapping> mappings,
                                 LeContext ctx, String fallback) {
        if (mappings == null || !mappings.containsKey(key)) {
            return fallback;
        }
        Object result = evaluator.evaluate(mappings.get(key), ctx);
        return result != null ? result.toString() : fallback;
    }

    private AmountValue resolveAmountValue(String prefix, LeContext ctx) {
        Object value = ctx.get(prefix + ".value");
        Object currency = ctx.get(prefix + ".currencyCode");
        if (value == null && currency == null) {
            return null;
        }
        AmountValue av = new AmountValue();
        if (value instanceof Number n) {
            av.setValue(n.doubleValue());
        }
        av.setCurrencyCode(currency != null ? currency.toString() : "EUR");
        return av;
    }

    private TransactionMetadata buildMetadata(Map<String, FieldMapping> mappings, LeContext ctx) {
        TransactionMetadata.TransactionMetadataBuilder mb = TransactionMetadata.builder();
        mb.schemeCode(resolveField("scheme_code", mappings, ctx, null));
        mb.paymentMethod(resolveField("payment_method", mappings, ctx, null));
        mb.acquirerName(resolveField("acquirer_name", mappings, ctx, null));
        mb.acquirerCountry(resolveField("acquirer_country", mappings, ctx, null));
        mb.sourceEventType(resolveField("source_event_type", mappings, ctx, null));
        mb.clientSettlementType(resolveField("client_settlement_type", mappings, ctx, null));
        mb.merchantCategoryCode(resolveField("merchant_category_code", mappings, ctx, null));
        mb.externalId(resolveField("external_id", mappings, ctx, null));
        mb.enhancedActionType(resolveField("enhanced_action_type", mappings, ctx, null));
        mb.invoiceNumber(resolveField("invoice_number", mappings, ctx, null));
        mb.reconciliationReference(resolveField("reconciliation_reference", mappings, ctx, null));
        mb.schemePartnerIdentifier(resolveField("scheme_partner_identifier", mappings, ctx, null));
        mb.settlementServiceName(resolveField("settlement_service_name", mappings, ctx, null));
        mb.settlementCountryCode(resolveField("settlement_country_code", mappings, ctx, null));
        mb.centralProcessingDate(resolveField("central_processing_date", mappings, ctx, null));
        mb.cashBatchId(resolveField("cash_batch_id", mappings, ctx, null));
        mb.payoutId(resolveField("payout_id", mappings, ctx, null));
        mb.billingDescriptor(resolveField("billing_descriptor", mappings, ctx, null));

        // Boolean fields
        String isNetSettledStr = resolveField("is_net_settled", mappings, ctx, null);
        if (isNetSettledStr != null) {
            mb.isNetSettled(Boolean.parseBoolean(isNetSettledStr));
        }

        return mb.build();
    }

    private Leg buildDefaultLeg(LeContext ctx, double amount, String currency,
                                 String entityId, String ckoEntityId) {
        return Leg.builder()
                .legId("default_L1")
                .legType("FUNDING")
                .legAmount(amount)
                .legCurrency(currency)
                .legStatus("PREDICTED")
                .valueDate(LocalDate.now().plusDays(1).toString())
                .fromParty(Party.builder().partyType("CKO_ENTITY").partyId(ckoEntityId).build())
                .toParty(Party.builder().partyType("CLIENT_ENTITY").partyId(entityId).build())
                .fees(List.of())
                .build();
    }

    private List<Leg> reapplyLegIds(List<Leg> legs, String transactionId) {
        List<Leg> result = new java.util.ArrayList<>();
        int counter = 1;
        for (Leg leg : legs) {
            String legId = transactionId + "_L" + counter++;
            // Rebuild with correct legId and reapply fee IDs
            List<Fee> fees = reapplyFeeIds(leg.getFees(), legId);
            result.add(Leg.builder()
                    .legId(legId)
                    .legType(leg.getLegType())
                    .legAmount(leg.getLegAmount())
                    .legCurrency(leg.getLegCurrency())
                    .legStatus(leg.getLegStatus())
                    .valueDate(leg.getValueDate())
                    .fromParty(leg.getFromParty())
                    .toParty(leg.getToParty())
                    .reserveAmount(leg.getReserveAmount())
                    .reserveCurrency(leg.getReserveCurrency())
                    .fees(fees)
                    .build());
        }
        return result;
    }

    private List<Fee> reapplyFeeIds(List<Fee> fees, String legId) {
        if (fees == null || fees.isEmpty()) return List.of();
        List<Fee> result = new java.util.ArrayList<>();
        int counter = 1;
        for (Fee fee : fees) {
            result.add(Fee.builder()
                    .feeId(legId + "_F" + counter++)
                    .feeType(fee.getFeeType())
                    .feeAmount(fee.getFeeAmount())
                    .feeCurrency(fee.getFeeCurrency())
                    .feeStatus(fee.getFeeStatus())
                    .taxAmount(fee.getTaxAmount())
                    .taxCurrency(fee.getTaxCurrency())
                    .passthrough(fee.getPassthrough())
                    .build());
        }
        return result;
    }
}
