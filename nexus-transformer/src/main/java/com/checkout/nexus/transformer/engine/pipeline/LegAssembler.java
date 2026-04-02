package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.LegConfig;
import com.checkout.nexus.transformer.engine.config.ConfigHolder;
import com.checkout.nexus.transformer.engine.config.ResolverExpression;
import com.checkout.nexus.transformer.engine.config.TransactionConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import com.checkout.nexus.transformer.model.Fee;
import com.checkout.nexus.transformer.model.Leg;
import com.checkout.nexus.transformer.model.Party;
import com.checkout.nexus.transformer.model.le.AmountValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Assembles {@link Leg} objects for a given product type and type
 * using the leg templates defined in {@link ConfigHolder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegAssembler {

    private final ConfigHolder config;
    private final ExpressionEvaluator evaluator;

    /**
     * Assembles all legs for the given product type/type combination.
     *
     * @param ctx            current LE context
     * @param productType    e.g. "ACQUIRING"
     * @param transactionType      e.g. "CAPTURE"
     * @param entityId       resolved client entity id
     * @param ckoEntityId    resolved CKO settlement entity id
     * @param acquirerEntity resolved CKO acquirer entity id
     * @param scheme         resolved scheme code (e.g. "VISA")
     * @return list of assembled legs (may be empty)
     */
    public List<Leg> assembleLegs(LeContext ctx, String productType, String transactionType,
                                   String entityId, String ckoEntityId, String acquirerEntity, String scheme) {
        Map<String, Map<String, TransactionConfig>> transactions = config.getTransactions();
        if (transactions == null) {
            return Collections.emptyList();
        }
        Map<String, TransactionConfig> familyTrades = transactions.get(productType);
        if (familyTrades == null) {
            return Collections.emptyList();
        }
        TransactionConfig transactionCfg = familyTrades.get(transactionType);
        if (transactionCfg == null || transactionCfg.getLegs() == null) {
            return Collections.emptyList();
        }

        // Derive a base transactionId for leg IDs — we use a placeholder here
        String transactionId = "txn";
        List<Leg> legs = new ArrayList<>();
        int legCounter = 1;

        for (LegConfig legCfg : transactionCfg.getLegs()) {
            // Evaluate the 'when' condition — skip leg when false
            if (legCfg.getWhen() != null && !evaluator.evaluateCondition(legCfg.getWhen(), ctx)) {
                continue;
            }

            String legId = transactionId + "_L" + legCounter++;

            // Resolve amount
            AmountValue amountValue = resolveAmount(legCfg.getAmount(), ctx);
            double legAmount = amountValue != null ? amountValue.getValue() : 0.0;
            String legCurrency = amountValue != null ? amountValue.getCurrencyCode() : "EUR";

            // Resolve status
            Object statusObj = evaluator.evaluate(legCfg.getStatus(), ctx);
            String legStatus = statusObj != null ? statusObj.toString() : "PREDICTED";

            // Resolve fees
            List<Fee> fees = resolveFees(legCfg.getFees(), ctx, legId);

            // Resolve rolling reserve
            AmountValue reserve = resolveAmount(legCfg.getRollingReserve(), ctx);

            // Determine value date
            String valueDate = resolveValueDate(ctx);

            // Build parties
            Party fromParty = buildParty(legCfg.getFromPartyType(), legCfg.getType(),
                    entityId, ckoEntityId, acquirerEntity, scheme, legCurrency);
            Party toParty = buildParty(legCfg.getToPartyType(), legCfg.getType(),
                    entityId, ckoEntityId, acquirerEntity, scheme, legCurrency);

            Leg.LegBuilder builder = Leg.builder()
                    .legId(legId)
                    .legType(legCfg.getType())
                    .legAmount(legAmount)
                    .legCurrency(legCurrency)
                    .legStatus(legStatus)
                    .valueDate(valueDate)
                    .fromParty(fromParty)
                    .toParty(toParty)
                    .fees(fees);

            if (reserve != null && reserve.getValue() > 0) {
                builder.reserveAmount(reserve.getValue()).reserveCurrency(reserve.getCurrencyCode());
            }

            legs.add(builder.build());
        }

        return legs;
    }

    // ------------------------------------------------------------------ private helpers

    private AmountValue resolveAmount(ResolverExpression expr, LeContext ctx) {
        if (expr == null) {
            return null;
        }
        Object result = evaluator.evaluate(expr, ctx);
        if (result instanceof AmountValue av) {
            return av;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Fee> resolveFees(ResolverExpression expr, LeContext ctx, String legId) {
        if (expr == null) {
            return Collections.emptyList();
        }
        Object result = evaluator.evaluate(expr, ctx);
        if (result instanceof List<?> list) {
            List<Fee> fees = new ArrayList<>();
            int counter = 1;
            for (Object item : list) {
                if (item instanceof Fee fee) {
                    // Assign leg-scoped fee IDs
                    fee = Fee.builder()
                            .feeId(legId + "_F" + counter++)
                            .feeType(fee.getFeeType())
                            .feeAmount(fee.getFeeAmount())
                            .feeCurrency(fee.getFeeCurrency())
                            .feeStatus(fee.getFeeStatus())
                            .taxAmount(fee.getTaxAmount())
                            .taxCurrency(fee.getTaxCurrency())
                            .passthrough(fee.getPassthrough())
                            .build();
                    fees.add(fee);
                }
            }
            return fees;
        }
        return Collections.emptyList();
    }

    private Party buildParty(String partyType, String legType,
                              String entityId, String ckoEntityId, String acquirerEntity,
                              String scheme, String currency) {
        if (partyType == null) {
            return Party.builder().partyType("UNKNOWN").build();
        }
        return switch (partyType) {
            case "CLIENT_ENTITY" -> Party.builder()
                    .partyType("CLIENT_ENTITY")
                    .partyId(entityId)
                    .currencyAccountId(caId(entityId, currency))
                    .build();
            case "CKO_ENTITY" -> {
                // SCHEME_SETTLEMENT legs use acquirer entity; FUNDING legs use settlement entity
                String ckoId = "SCHEME_SETTLEMENT".equals(legType) ? acquirerEntity : ckoEntityId;
                yield Party.builder()
                        .partyType("CKO_ENTITY")
                        .partyId(ckoId)
                        .currencyAccountId(caId(ckoId, currency))
                        .build();
            }
            case "SCHEME" -> Party.builder()
                    .partyType("SCHEME")
                    .partyId(scheme)
                    .build();
            default -> Party.builder().partyType(partyType).build();
        };
    }

    private String caId(String partyId, String currency) {
        if (partyId == null) partyId = "unknown";
        if (currency == null) currency = "eur";
        return "ca_" + partyId.toLowerCase() + "_" + currency.toLowerCase() + "_001";
    }

    private String resolveValueDate(LeContext ctx) {
        // Try SD expectedValueDate
        Object vd = ctx.get("SD.metadata.expectedValueDate");
        if (vd != null) return vd.toString();
        // Try FIAPI valueDate
        vd = ctx.get("FIAPI.metadata.valueDate");
        if (vd != null) return vd.toString();
        // Try COS invoiceDate
        vd = ctx.get("COS.metadata.invoiceDate");
        if (vd != null) return vd.toString();
        // Try CASH valueDate
        vd = ctx.get("CASH.standardMetadata.valueDate");
        if (vd != null) return vd.toString();
        // Default to tomorrow
        return LocalDate.now().plusDays(1).toString();
    }
}
