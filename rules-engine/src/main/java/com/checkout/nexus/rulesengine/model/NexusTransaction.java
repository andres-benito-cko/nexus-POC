package com.checkout.nexus.rulesengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NexusTransaction {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("parent_transaction_id")
    private String parentTransactionId;

    @JsonProperty("action_id")
    private String actionId;

    @JsonProperty("action_root_id")
    private String actionRootId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("entity")
    private Entity entity;

    @JsonProperty("cko_entity_id")
    private String ckoEntityId;

    @JsonProperty("processed_at")
    private String processedAt;

    @JsonProperty("period_start")
    private String periodStart;

    @JsonProperty("period_end")
    private String periodEnd;

    @JsonProperty("trades")
    private List<Trade> trades;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entity {
        @JsonProperty("id")
        private String id;
        @JsonProperty("sub_id")
        private String subId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Trade {
        @JsonProperty("trade_id")
        private String tradeId;
        @JsonProperty("trade_family")
        private String tradeFamily;
        @JsonProperty("trade_type")
        private String tradeType;
        @JsonProperty("trade_status")
        private String tradeStatus;
        @JsonProperty("trade_amount")
        private double tradeAmount;
        @JsonProperty("trade_currency")
        private String tradeCurrency;
        @JsonProperty("trade_date")
        private String tradeDate;
        @JsonProperty("legs")
        private List<Leg> legs;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Leg {
        @JsonProperty("leg_id")
        private String legId;
        @JsonProperty("leg_type")
        private String legType;
        @JsonProperty("leg_amount")
        private double legAmount;
        @JsonProperty("leg_currency")
        private String legCurrency;
        @JsonProperty("leg_status")
        private String legStatus;
        @JsonProperty("value_date")
        private String valueDate;
        @JsonProperty("from_party")
        private Party fromParty;
        @JsonProperty("to_party")
        private Party toParty;
        @JsonProperty("fees")
        private List<Fee> fees;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Party {
        @JsonProperty("party_type")
        private String partyType;
        @JsonProperty("party_id")
        private String partyId;
        @JsonProperty("currency_account_id")
        private String currencyAccountId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fee {
        @JsonProperty("fee_id")
        private String feeId;
        @JsonProperty("fee_type")
        private String feeType;
        @JsonProperty("fee_amount")
        private double feeAmount;
        @JsonProperty("fee_currency")
        private String feeCurrency;
        @JsonProperty("fee_status")
        private String feeStatus;
    }
}
