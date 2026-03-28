package com.checkout.nexus.transformer.service;

import com.checkout.nexus.transformer.engine.NexusEngine;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.*;
import com.checkout.nexus.transformer.model.le.*;
import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import com.checkout.nexus.transformer.model.le.CosEvent;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import com.checkout.nexus.transformer.model.le.CashEvent;
import com.checkout.nexus.transformer.validation.DlqHandler;
import com.checkout.nexus.transformer.validation.NexusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransformerService {

    private static final String OUTPUT_TOPIC = "nexus.blocks";

    private final KafkaTemplate<String, NexusBlock> kafkaTemplate;
    private final BlockStore blockStore;
    private final NexusEngine nexusEngine;
    private final NexusValidator nexusValidator;
    private final DlqHandler dlqHandler;

    @KafkaListener(topics = "le.linked.transactions", groupId = "nexus-transformer")
    public void onLeTransaction(LeLinkedTransaction le) {
        log.info("Received LE transaction: actionId={}, version={}", le.getActionId(), le.getTransactionVersion());

        NexusBlock nexus = null;
        try {
            // Create context and transform via NexusEngine
            LeContext ctx = new LeContext(le);
            nexus = nexusEngine.transform(ctx);

            // Validate against schema
            NexusValidator.ValidationResult validation = nexusValidator.validate(nexus);
            if (validation.isValid()) {
                blockStore.add(nexus);
                kafkaTemplate.send(OUTPUT_TOPIC, nexus.getNexusId(), nexus);
                log.info("Produced Nexus transaction: nexusId={}, status={}", nexus.getNexusId(), nexus.getStatus());
            } else {
                log.warn("Nexus transaction failed validation: actionId={}, errors={}",
                        le.getActionId(), validation.getErrors());
                dlqHandler.sendToDlq(le, nexus, validation.getErrors());
            }
        } catch (Exception e) {
            log.error("Error transforming LE transaction: actionId={}", le.getActionId(), e);
            dlqHandler.sendToDlq(le, nexus,
                    List.of("Transformation error: " + e.getMessage()));
        }
    }

    /**
     * Transforms a raw LE transaction into a NexusBlock.
     * Delegates to {@link NexusEngine} (backward-compatible entry point).
     */
    public NexusBlock transform(LeLinkedTransaction le) {
        return nexusEngine.transform(new LeContext(le));
    }

    // ------------------------------------------------------------------ legacy implementation (preserved for reference)

    @SuppressWarnings("unused")
    private NexusBlock transformLegacy(LeLinkedTransaction le) {
        // Determine product type and type from available data
        String productType = determineProductType(le);
        String transactionType = determineTransactionType(le, productType);
        String transactionStatus = determineTransactionStatus(le);
        String status = determineBlockStatus(le);

        // Extract header fields
        String processedAt = extractProcessedAt(le);
        String entityId = extractEntityId(le);
        String ckoEntityId = extractCkoEntityId(le);

        // Build trade amount/currency from gateway
        double transactionAmount = 0;
        String transactionCurrency = "EUR";
        String transactionDate = LocalDate.now().toString();

        if (!le.getGatewayEvents().isEmpty()) {
            GatewayEvent gw = le.getGatewayEvents().get(0);
            if (gw.getAmount() != null) {
                transactionAmount = gw.getAmount().getValue();
                transactionCurrency = gw.getAmount().getCurrencyCode();
            }
            if (gw.getProcessedOn() != null) {
                transactionDate = gw.getProcessedOn().substring(0, 10);
            }
        }

        // Build metadata
        TransactionMetadata metadata = buildMetadata(le);

        // Build legs
        List<Leg> legs = buildLegs(le, productType, transactionType, transactionAmount, transactionCurrency, entityId, ckoEntityId);

        Transaction transaction = Transaction.builder()
            .transactionId(le.getActionId() + "_1")
            .productType(productType)
            .transactionType(transactionType)
            .transactionStatus(transactionStatus)
            .transactionAmount(transactionAmount)
            .transactionCurrency(transactionCurrency)
            .transactionDate(transactionDate)
            .metadata(metadata)
            .legs(legs)
            .build();

        return NexusBlock.builder()
            .nexusId(le.getActionId())
            .parentNexusId(le.getActionRootId())
            .actionId(le.getActionId())
            .actionRootId(le.getActionRootId())
            .status(status)
            .entity(Entity.builder().id(entityId).build())
            .ckoEntityId(ckoEntityId)
            .processedAt(processedAt)
            .transactions(List.of(transaction))
            .build();
    }

    private String determineProductType(LeLinkedTransaction le) {
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getActionType() != null) {
                String actionType = bce.getMetadata().getActionType();
                if (actionType.contains("CARD_PAYOUT")) return "PAYOUT";
                if (actionType.contains("TOP_UP")) return "TOPUP";
            }
        }
        if (!le.getCashEvents().isEmpty() && le.getGatewayEvents().isEmpty()) {
            return "CASH";
        }
        // Default to ACQUIRING for card transactions
        return "ACQUIRING";
    }

    private String determineTransactionType(LeLinkedTransaction le, String productType) {
        // Check FIAPI action type first
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getActionType() != null) {
                String actionType = bce.getMetadata().getActionType();
                if (actionType.contains("CAPTURE")) return "CAPTURE";
                if (actionType.contains("REFUND")) return "REFUND";
                if (actionType.contains("AUTHORIZATION")) return "AUTH";
                if (actionType.contains("CHARGEBACK")) return "CHARGEBACK";
                if (actionType.contains("CARD_PAYOUT")) return "CREDIT";
                if (actionType.contains("TOP_UP")) return "CREDIT";
            }
        }

        // Fallback to gateway event type
        if (!le.getGatewayEvents().isEmpty()) {
            String eventType = le.getGatewayEvents().get(0).getEventType();
            if (eventType != null) {
                if (eventType.contains("captured") || eventType.contains("Captured")) return "CAPTURE";
                if (eventType.contains("refund") || eventType.contains("Refund")) return "REFUND";
                if (eventType.contains("chargeback") || eventType.contains("Chargeback")) return "CHARGEBACK";
                if (eventType.contains("paid_out")) return "CREDIT";
                if (eventType.contains("top_up")) return "CREDIT";
            }
        }

        // Check SD transaction type
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getMetadata() != null && sd.getMetadata().getTransactionType() != null) {
                String txnType = sd.getMetadata().getTransactionType();
                if ("Capture".equals(txnType) || "Aft Capture".equals(txnType)) return "CAPTURE";
                if ("Refund".equals(txnType)) return "REFUND";
                if ("Chargeback".equals(txnType)) return "CHARGEBACK";
            }
        }

        if ("CASH".equals(productType)) return "SETTLEMENT";

        return "CAPTURE"; // Default
    }

    private String determineTransactionStatus(LeLinkedTransaction le) {
        boolean hasSd = !le.getSchemeSettlementEvents().isEmpty();
        boolean hasCash = !le.getCashEvents().isEmpty();
        boolean hasFiapi = !le.getBalancesChangedEvents().isEmpty();

        if (hasSd || hasCash) return "SETTLED";

        // Determine from transaction type
        String productType = determineProductType(le);
        String transactionType = determineTransactionType(le, productType);

        if (hasFiapi) {
            return switch (transactionType) {
                case "CAPTURE" -> "CAPTURED";
                case "REFUND" -> "INITIATED";
                case "AUTH" -> "AUTHORISED";
                case "CHARGEBACK" -> "RECEIVED";
                case "CREDIT" -> "INITIATED";
                default -> "CAPTURED";
            };
        }

        // GW only
        return switch (transactionType) {
            case "CAPTURE" -> "CAPTURED";
            case "REFUND" -> "INITIATED";
            case "AUTH" -> "AUTHORISED";
            case "CHARGEBACK" -> "RECEIVED";
            default -> "CAPTURED";
        };
    }

    private String determineBlockStatus(LeLinkedTransaction le) {
        boolean hasFiapi = !le.getBalancesChangedEvents().isEmpty();
        boolean hasSd = !le.getSchemeSettlementEvents().isEmpty();

        if (hasSd) return "LIVE";
        if (hasFiapi) return "LIVE";
        return "NOT_LIVE";
    }

    private String extractProcessedAt(LeLinkedTransaction le) {
        // Priority: GW processed_on > FIAPI > envelope
        if (!le.getGatewayEvents().isEmpty() && le.getGatewayEvents().get(0).getProcessedOn() != null) {
            return le.getGatewayEvents().get(0).getProcessedOn();
        }
        if (!le.getGatewayEvents().isEmpty() && le.getGatewayEvents().get(0).getEnvelope() != null) {
            return le.getGatewayEvents().get(0).getEnvelope().getOriginEventTimestamp();
        }
        return java.time.Instant.now().toString();
    }

    private String extractEntityId(LeLinkedTransaction le) {
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getClientId() != null) {
                return bce.getMetadata().getClientId();
            }
        }
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getMetadata() != null && sd.getMetadata().getClientId() != null) {
                return sd.getMetadata().getClientId();
            }
        }
        if (!le.getCashEvents().isEmpty()) {
            CashEvent cash = le.getCashEvents().get(0);
            if (cash.getStandardMetadata() != null && cash.getStandardMetadata().getEntityId() != null) {
                return cash.getStandardMetadata().getEntityId();
            }
        }
        return "unknown_entity";
    }

    private String extractCkoEntityId(LeLinkedTransaction le) {
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getSettlementCompanyCkoLegalEntityCode() != null) {
                return bce.getMetadata().getSettlementCompanyCkoLegalEntityCode();
            }
        }
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getMetadata() != null && sd.getMetadata().getSettlementCompanyCkoLegalEntityCode() != null) {
                return sd.getMetadata().getSettlementCompanyCkoLegalEntityCode();
            }
        }
        return "CKO_UNKNOWN";
    }

    private TransactionMetadata buildMetadata(LeLinkedTransaction le) {
        TransactionMetadata.TransactionMetadataBuilder mb = TransactionMetadata.builder();

        // GW fields
        if (!le.getGatewayEvents().isEmpty()) {
            GatewayEvent gw = le.getGatewayEvents().get(0);
            mb.sourceEventType(gw.getEventType());
            mb.acquirerName(gw.getAcquirerName());
            mb.acquirerCountry(gw.getAcquirerCountry());
            mb.cashBatchId(gw.getCashBatchId());
        }

        // FIAPI overrides
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent.Metadata bm = le.getBalancesChangedEvents().get(0).getMetadata();
            if (bm != null) {
                if (bm.getSchemeCode() != null) mb.schemeCode(bm.getSchemeCode());
                if (bm.getPaymentMethod() != null) mb.paymentMethod(bm.getPaymentMethod());
                if (bm.getAcquirerName() != null) mb.acquirerName(bm.getAcquirerName());
                if (bm.getAcquirerCountry() != null) mb.acquirerCountry(bm.getAcquirerCountry());
                if (bm.getClientSettlementType() != null) mb.clientSettlementType(bm.getClientSettlementType());
                if (bm.getMerchantCategoryCode() != null) mb.merchantCategoryCode(bm.getMerchantCategoryCode());
                if (bm.getExternalId() != null) mb.externalId(bm.getExternalId());
                if (bm.getEnhancedActionType() != null) mb.enhancedActionType(bm.getEnhancedActionType());
                if (bm.getInvoiceNumber() != null) mb.invoiceNumber(bm.getInvoiceNumber());
            }
        }

        // COS overrides
        if (!le.getCosEvents().isEmpty()) {
            CosEvent.Metadata cm = le.getCosEvents().get(0).getMetadata();
            if (cm != null) {
                if (cm.getReconciliationReference() != null) mb.reconciliationReference(cm.getReconciliationReference());
                if (cm.getSchemePartnerIdentifier() != null) mb.schemePartnerIdentifier(cm.getSchemePartnerIdentifier());
                if (cm.getIsNetSettled() != null) mb.isNetSettled(cm.getIsNetSettled());
            }
        }

        // SD overrides (authoritative)
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent.Metadata sm = le.getSchemeSettlementEvents().get(0).getMetadata();
            if (sm != null) {
                if (sm.getScheme() != null) mb.schemeCode(sm.getScheme());
                if (sm.getAcquirerName() != null) mb.acquirerName(sm.getAcquirerName());
                if (sm.getAcquirerCountry() != null) mb.acquirerCountry(sm.getAcquirerCountry());
                if (sm.getSettlementServiceName() != null) mb.settlementServiceName(sm.getSettlementServiceName());
                if (sm.getSettlementCountryCode() != null) mb.settlementCountryCode(sm.getSettlementCountryCode());
                if (sm.getCentralProcessingDate() != null) mb.centralProcessingDate(sm.getCentralProcessingDate());
            }
        }

        // Cash metadata
        if (!le.getCashEvents().isEmpty()) {
            CashEvent.StandardMetadata csm = le.getCashEvents().get(0).getStandardMetadata();
            if (csm != null) {
                if (csm.getScheme() != null && mb.build().getSchemeCode() == null) mb.schemeCode(csm.getScheme());
                if (csm.getPayoutId() != null) mb.payoutId(csm.getPayoutId());
                if (csm.getBillingDescriptor() != null) mb.billingDescriptor(csm.getBillingDescriptor());
            }
        }

        return mb.build();
    }

    private List<Leg> buildLegs(LeLinkedTransaction le, String productType, String transactionType,
            double transactionAmount, String transactionCurrency, String entityId, String ckoEntityId) {
        List<Leg> legs = new ArrayList<>();
        String transactionId = le.getActionId() + "_1";
        int legCounter = 1;

        boolean hasSd = !le.getSchemeSettlementEvents().isEmpty();
        boolean hasCos = !le.getCosEvents().isEmpty();
        boolean hasFiapi = !le.getBalancesChangedEvents().isEmpty();
        boolean hasCash = !le.getCashEvents().isEmpty();

        // Build SCHEME_SETTLEMENT leg for ACQUIRING captures/refunds/chargebacks
        if ("ACQUIRING".equals(productType) &&
                ("CAPTURE".equals(transactionType) || "REFUND".equals(transactionType) || "CHARGEBACK".equals(transactionType))) {

            String ssLegId = transactionId + "_L" + legCounter++;
            double ssAmount = transactionAmount;
            String ssCurrency = transactionCurrency;
            String ssStatus = "PREDICTED";
            String ssValueDate = LocalDate.now().plusDays(1).toString();

            // Use SD data if available (ACTUAL)
            if (hasSd) {
                SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
                if (sd.getPayload() != null && sd.getPayload().getSettlementAmount() != null) {
                    ssAmount = sd.getPayload().getSettlementAmount().getMoney().getValue();
                    ssCurrency = sd.getPayload().getSettlementAmount().getMoney().getCurrencyCode();
                }
                ssStatus = "ACTUAL";
                if (sd.getMetadata() != null && sd.getMetadata().getExpectedValueDate() != null) {
                    ssValueDate = sd.getMetadata().getExpectedValueDate();
                }
            } else if (hasCos && !le.getCosEvents().isEmpty()) {
                CosEvent cos = le.getCosEvents().get(0);
                if (cos.getMetadata() != null && cos.getMetadata().getInvoiceDate() != null) {
                    ssValueDate = cos.getMetadata().getInvoiceDate();
                }
            }

            // Determine direction based on transaction type
            Party fromParty, toParty;
            String acquirerEntity = extractAcquirerEntity(le, ckoEntityId);

            if ("CAPTURE".equals(transactionType)) {
                // SCHEME -> CKO_ENTITY
                fromParty = Party.builder().partyType("SCHEME").partyId(extractScheme(le)).build();
                toParty = Party.builder().partyType("CKO_ENTITY").partyId(acquirerEntity)
                    .currencyAccountId("ca_" + acquirerEntity.toLowerCase() + "_" + ssCurrency.toLowerCase() + "_001").build();
            } else {
                // CKO_ENTITY -> SCHEME (refund/chargeback)
                fromParty = Party.builder().partyType("CKO_ENTITY").partyId(acquirerEntity)
                    .currencyAccountId("ca_" + acquirerEntity.toLowerCase() + "_" + ssCurrency.toLowerCase() + "_001").build();
                toParty = Party.builder().partyType("SCHEME").partyId(extractScheme(le)).build();
            }

            // Build fees for SCHEME_SETTLEMENT leg
            List<Fee> ssFees = buildSchemeSettlementFees(le, ssLegId, hasSd);

            legs.add(Leg.builder()
                .legId(ssLegId)
                .legType("SCHEME_SETTLEMENT")
                .legAmount(ssAmount)
                .legCurrency(ssCurrency)
                .legStatus(ssStatus)
                .valueDate(ssValueDate)
                .fromParty(fromParty)
                .toParty(toParty)
                .fees(ssFees)
                .build());
        }

        // Build FUNDING leg for ACQUIRING, PAYOUT, TOPUP
        if (hasFiapi && ("ACQUIRING".equals(productType) || "PAYOUT".equals(productType) || "TOPUP".equals(productType))) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            String fLegId = transactionId + "_L" + legCounter++;

            double fAmount = transactionAmount;
            String fCurrency = transactionCurrency;
            String fValueDate = LocalDate.now().plusDays(2).toString();

            if (bce.getMetadata() != null) {
                if (bce.getMetadata().getValueDate() != null) {
                    fValueDate = bce.getMetadata().getValueDate();
                }
            }
            if (bce.getActions() != null && !bce.getActions().isEmpty()) {
                BalancesChangedEvent.Changes changes = bce.getActions().get(0).getChanges();
                if (changes != null && changes.getPending() != null && changes.getPending().getHoldingAmount() != null) {
                    fAmount = changes.getPending().getHoldingAmount().getValue();
                    fCurrency = changes.getPending().getHoldingAmount().getCurrencyCode();
                }
            }

            String fStatus = hasSd ? "ACTUAL" : "PREDICTED";

            // Determine direction
            Party fromParty, toParty;
            if ("REFUND".equals(transactionType) || "CHARGEBACK".equals(transactionType) || "TOPUP".equals(productType)) {
                // CLIENT -> CKO
                fromParty = Party.builder().partyType("CLIENT_ENTITY").partyId(entityId)
                    .currencyAccountId("ca_" + entityId + "_" + fCurrency.toLowerCase() + "_001").build();
                toParty = Party.builder().partyType("CKO_ENTITY").partyId(ckoEntityId)
                    .currencyAccountId("ca_" + ckoEntityId.toLowerCase() + "_" + fCurrency.toLowerCase() + "_001").build();
            } else {
                // CKO -> CLIENT (capture, payout)
                fromParty = Party.builder().partyType("CKO_ENTITY").partyId(ckoEntityId)
                    .currencyAccountId("ca_" + ckoEntityId.toLowerCase() + "_" + fCurrency.toLowerCase() + "_001").build();
                toParty = Party.builder().partyType("CLIENT_ENTITY").partyId(entityId)
                    .currencyAccountId("ca_" + entityId + "_" + fCurrency.toLowerCase() + "_001").build();
            }

            // Reserve
            Double reserveAmount = null;
            String reserveCurrency = null;
            if (bce.getActions() != null && !bce.getActions().isEmpty()) {
                BalancesChangedEvent.Changes changes = bce.getActions().get(0).getChanges();
                if (changes != null && changes.getRollingReserve() != null && changes.getRollingReserve().getValue() > 0) {
                    reserveAmount = changes.getRollingReserve().getValue();
                    reserveCurrency = changes.getRollingReserve().getCurrencyCode();
                }
            }

            // Funding leg fees (PROCESSING_FEE, ACQUIRER_FEE)
            List<Fee> fFees = buildFundingFees(le, fLegId, fCurrency, fStatus);

            Leg.LegBuilder fundingLeg = Leg.builder()
                .legId(fLegId)
                .legType("FUNDING")
                .legAmount(fAmount)
                .legCurrency(fCurrency)
                .legStatus(fStatus)
                .valueDate(fValueDate)
                .fromParty(fromParty)
                .toParty(toParty)
                .fees(fFees);

            if (reserveAmount != null) {
                fundingLeg.reserveAmount(reserveAmount).reserveCurrency(reserveCurrency);
            }

            legs.add(fundingLeg.build());
        }

        // Build FUNDING leg for CASH standalone
        if ("CASH".equals(productType) && hasCash) {
            CashEvent cash = le.getCashEvents().get(0);
            String cLegId = transactionId + "_L" + legCounter++;

            double cAmount = cash.getStandardPayload() != null ? cash.getStandardPayload().getAmount().getValue() : transactionAmount;
            String cCurrency = cash.getStandardPayload() != null ? cash.getStandardPayload().getAmount().getCurrencyCode() : transactionCurrency;
            String cValueDate = cash.getStandardMetadata() != null && cash.getStandardMetadata().getValueDate() != null
                ? cash.getStandardMetadata().getValueDate() : LocalDate.now().toString();

            String direction = cash.getStandardPayload() != null ? cash.getStandardPayload().getDirection() : "credit";
            String legalEntity = cash.getStandardMetadata() != null ? cash.getStandardMetadata().getLegalEntity() : ckoEntityId;

            Party fromParty, toParty;
            if ("credit".equals(direction)) {
                fromParty = Party.builder().partyType("CKO_ENTITY").partyId(legalEntity).build();
                toParty = Party.builder().partyType("CLIENT_ENTITY").partyId(entityId).build();
            } else {
                fromParty = Party.builder().partyType("CLIENT_ENTITY").partyId(entityId).build();
                toParty = Party.builder().partyType("CKO_ENTITY").partyId(legalEntity).build();
            }

            legs.add(Leg.builder()
                .legId(cLegId)
                .legType("FUNDING")
                .legAmount(cAmount)
                .legCurrency(cCurrency)
                .legStatus("ACTUAL")
                .valueDate(cValueDate)
                .fromParty(fromParty)
                .toParty(toParty)
                .fees(List.of())
                .build());
        }

        // Ensure at least one leg exists
        if (legs.isEmpty()) {
            legs.add(Leg.builder()
                .legId(transactionId + "_L1")
                .legType("FUNDING")
                .legAmount(transactionAmount)
                .legCurrency(transactionCurrency)
                .legStatus("PREDICTED")
                .valueDate(LocalDate.now().plusDays(1).toString())
                .fromParty(Party.builder().partyType("CKO_ENTITY").partyId(ckoEntityId).build())
                .toParty(Party.builder().partyType("CLIENT_ENTITY").partyId(entityId).build())
                .fees(List.of())
                .build());
        }

        return legs;
    }

    private List<Fee> buildSchemeSettlementFees(LeLinkedTransaction le, String legId, boolean hasSd) {
        List<Fee> fees = new ArrayList<>();
        int feeCounter = 1;

        if (hasSd) {
            // Use SD fees (ACTUAL)
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getPayload() != null && sd.getPayload().getFees() != null) {
                for (SchemeSettlementEvent.SdFee sdFee : sd.getPayload().getFees()) {
                    fees.add(Fee.builder()
                        .feeId(legId + "_F" + feeCounter++)
                        .feeType(mapSdFeeType(sdFee.getType()))
                        .feeAmount(sdFee.getRoundedAmount())
                        .feeCurrency(sdFee.getCurrencyCode())
                        .feeStatus("ACTUAL")
                        .taxAmount(sdFee.getTaxAmount() > 0 ? sdFee.getTaxAmount() : null)
                        .build());
                }
            }
        } else if (!le.getCosEvents().isEmpty()) {
            // Use COS fees (PREDICTED)
            for (CosEvent cos : le.getCosEvents()) {
                if (cos.getPayload() != null && cos.getPayload().getFee() != null) {
                    String feeType = mapCosFeeType(cos.getPayload().getFeeType());
                    Double taxAmount = null;
                    String taxCurrency = null;
                    if (cos.getPayload().getVat() != null && cos.getPayload().getVat().getValue() > 0) {
                        taxAmount = cos.getPayload().getVat().getValue();
                        taxCurrency = cos.getPayload().getVat().getCurrencyCode();
                    }

                    fees.add(Fee.builder()
                        .feeId(legId + "_F" + feeCounter++)
                        .feeType(feeType)
                        .feeAmount(cos.getPayload().getFee().getValue())
                        .feeCurrency(cos.getPayload().getFee().getCurrencyCode())
                        .feeStatus("PREDICTED")
                        .passthrough("Passthrough".equals(cos.getPayload().getFeeSubType()) ? true : null)
                        .taxAmount(taxAmount)
                        .taxCurrency(taxCurrency)
                        .build());
                }
            }
        }

        return fees;
    }

    private List<Fee> buildFundingFees(LeLinkedTransaction le, String legId, String currency, String status) {
        List<Fee> fees = new ArrayList<>();
        // Add a PROCESSING_FEE if FIAPI data present (simplified for POC)
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getActions() != null) {
                for (BalancesChangedEvent.Action action : bce.getActions()) {
                    if (action.getActionMetadata() != null && "fee".equals(action.getActionMetadata().getAmountType())) {
                        String feeType = mapFiapiFeeType(action.getActionMetadata().getFeeType());
                        fees.add(Fee.builder()
                            .feeId(legId + "_F" + (fees.size() + 1))
                            .feeType(feeType)
                            .feeAmount(0) // Amount would come from changes
                            .feeCurrency(currency)
                            .feeStatus(status)
                            .build());
                    }
                }
            }
        }
        return fees;
    }

    private String extractScheme(LeLinkedTransaction le) {
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getMetadata() != null && sd.getMetadata().getScheme() != null) {
                return sd.getMetadata().getScheme();
            }
        }
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getSchemeCode() != null) {
                return bce.getMetadata().getSchemeCode();
            }
        }
        return "UNKNOWN_SCHEME";
    }

    private String extractAcquirerEntity(LeLinkedTransaction le, String fallback) {
        if (!le.getBalancesChangedEvents().isEmpty()) {
            BalancesChangedEvent bce = le.getBalancesChangedEvents().get(0);
            if (bce.getMetadata() != null && bce.getMetadata().getAcquirerCompanyCkoLegalEntityCode() != null) {
                return bce.getMetadata().getAcquirerCompanyCkoLegalEntityCode();
            }
        }
        if (!le.getSchemeSettlementEvents().isEmpty()) {
            SchemeSettlementEvent sd = le.getSchemeSettlementEvents().get(0);
            if (sd.getMetadata() != null && sd.getMetadata().getAcquirerCompanyCkoLegalEntityCode() != null) {
                return sd.getMetadata().getAcquirerCompanyCkoLegalEntityCode();
            }
        }
        return fallback;
    }

    private String mapSdFeeType(String sdType) {
        if (sdType == null) return "SCHEME_FEE";
        return switch (sdType) {
            case "INTERCHANGE_FEE" -> "INTERCHANGE";
            case "SCHEME_FEE" -> "SCHEME_FEE";
            default -> "SCHEME_FEE";
        };
    }

    private String mapCosFeeType(String cosType) {
        if (cosType == null) return "SCHEME_FEE";
        return switch (cosType) {
            case "FEE_TYPE_INTERCHANGE" -> "INTERCHANGE";
            case "FEE_TYPE_SCHEME" -> "SCHEME_FEE";
            default -> "SCHEME_FEE";
        };
    }

    private String mapFiapiFeeType(String fiapiType) {
        if (fiapiType == null) return "PROCESSING_FEE";
        return switch (fiapiType) {
            case "Gateway" -> "PROCESSING_FEE";
            case "Scheme" -> "SCHEME_FEE";
            case "Interchange" -> "INTERCHANGE";
            default -> "PROCESSING_FEE";
        };
    }
}
