package com.checkout.nexus.transformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Transaction {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("product_type")
    private String productType;

    @JsonProperty("transaction_type")
    private String transactionType;

    @JsonProperty("transaction_status")
    private String transactionStatus;

    @JsonProperty("transaction_amount")
    private double transactionAmount;

    @JsonProperty("transaction_currency")
    private String transactionCurrency;

    @JsonProperty("transaction_date")
    private String transactionDate;

    @JsonProperty("metadata")
    private TransactionMetadata metadata;

    @JsonProperty("legs")
    private List<Leg> legs;
}
