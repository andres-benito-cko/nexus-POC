package com.checkout.nexus.rulesengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka message representation of a ledger entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryMessage {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("ruleId")
    private UUID ruleId;

    @JsonProperty("ruleName")
    private String ruleName;

    @JsonProperty("nexusId")
    private String nexusId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("legId")
    private String legId;

    @JsonProperty("debitAccount")
    private String debitAccount;

    @JsonProperty("creditAccount")
    private String creditAccount;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("productType")
    private String productType;

    @JsonProperty("transactionType")
    private String transactionType;

    @JsonProperty("transactionStatus")
    private String transactionStatus;
}
