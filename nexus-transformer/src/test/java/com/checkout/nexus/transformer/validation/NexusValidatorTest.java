package com.checkout.nexus.transformer.validation;

import com.checkout.nexus.transformer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NexusValidatorTest {

    private NexusValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NexusValidator();
    }

    @Test
    @DisplayName("Valid NexusTransaction passes validation")
    void validTransaction_passes() {
        NexusTransaction tx = buildValidTransaction();
        NexusValidator.ValidationResult result = validator.validate(tx);
        assertThat(result.getErrors()).as("Validation errors: " + result.getErrors()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Transaction missing required fields fails validation with errors listed")
    void missingRequiredFields_failsWithErrors() {
        // Completely empty transaction
        NexusTransaction tx = NexusTransaction.builder().build();
        NexusValidator.ValidationResult result = validator.validate(tx);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Transaction with invalid status fails validation")
    void invalidStatus_failsValidation() {
        NexusTransaction tx = buildValidTransaction();
        // Status must be one of NOT_LIVE/LIVE/DEAD per schema
        tx.setStatus("INVALID_STATUS");
        NexusValidator.ValidationResult result = validator.validate(tx);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Transaction missing trades fails validation")
    void missingTrades_failsValidation() {
        NexusTransaction tx = buildValidTransaction();
        tx.setTrades(null);
        NexusValidator.ValidationResult result = validator.validate(tx);
        assertThat(result.isValid()).isFalse();
    }

    // ------------------------------------------------------------------ helper

    private NexusTransaction buildValidTransaction() {
        Leg leg = Leg.builder()
                .legId("action-001_1_L1")
                .legType("FUNDING")
                .legAmount(100.0)
                .legCurrency("EUR")
                .legStatus("PREDICTED")
                .valueDate("2026-01-15")
                .fromParty(Party.builder().partyType("CKO_ENTITY").partyId("CKO_UK").build())
                .toParty(Party.builder().partyType("CLIENT_ENTITY").partyId("client-001").build())
                .fees(List.of())
                .build();

        Trade trade = Trade.builder()
                .tradeId("action-001_1")
                .tradeFamily("ACQUIRING")
                .tradeType("CAPTURE")
                .tradeStatus("CAPTURED")
                .tradeAmount(100.0)
                .tradeCurrency("EUR")
                .tradeDate("2026-01-14")
                .metadata(TradeMetadata.builder().build())
                .legs(List.of(leg))
                .build();

        return NexusTransaction.builder()
                .transactionId("action-001")
                .parentTransactionId("root-001")
                .actionId("action-001")
                .actionRootId("root-001")
                .status("NOT_LIVE")
                .entity(Entity.builder().id("client-001").build())
                .ckoEntityId("CKO_UK")
                .processedAt("2026-01-14T10:00:00Z")
                .trades(List.of(trade))
                .build();
    }
}
