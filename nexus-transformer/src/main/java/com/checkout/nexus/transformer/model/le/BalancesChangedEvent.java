package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalancesChangedEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("actions")
    private List<Action> actions;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("clientId")
        private String clientId;
        @JsonProperty("entityId")
        private String entityId;
        @JsonProperty("entitySubId")
        private String entitySubId;
        @JsonProperty("settlementCompanyCkoLegalEntityCode")
        private String settlementCompanyCkoLegalEntityCode;
        @JsonProperty("acquirerCompanyCkoLegalEntityCode")
        private String acquirerCompanyCkoLegalEntityCode;
        @JsonProperty("schemeCode")
        private String schemeCode;
        @JsonProperty("paymentMethod")
        private String paymentMethod;
        @JsonProperty("sourceEventType")
        private String sourceEventType;
        @JsonProperty("actionType")
        private String actionType;
        @JsonProperty("enhancedActionType")
        private String enhancedActionType;
        @JsonProperty("externalId")
        private String externalId;
        @JsonProperty("currencyAccountId")
        private String currencyAccountId;
        @JsonProperty("merchantCategoryCode")
        private String merchantCategoryCode;
        @JsonProperty("acquirerName")
        private String acquirerName;
        @JsonProperty("acquirerCountry")
        private String acquirerCountry;
        @JsonProperty("clientSettlementType")
        private String clientSettlementType;
        @JsonProperty("invoiceNumber")
        private String invoiceNumber;
        @JsonProperty("valueDate")
        private String valueDate;
        @JsonProperty("periodStart")
        private String periodStart;
        @JsonProperty("periodEnd")
        private String periodEnd;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Action {
        @JsonProperty("changes")
        private Changes changes;
        @JsonProperty("actionMetadata")
        private ActionMetadata actionMetadata;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changes {
        @JsonProperty("pending")
        private PendingChange pending;
        @JsonProperty("rollingReserve")
        private AmountValue rollingReserve;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PendingChange {
        @JsonProperty("holdingAmount")
        private AmountValue holdingAmount;
        @JsonProperty("processingAmount")
        private AmountValue processingAmount;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionMetadata {
        @JsonProperty("amountType")
        private String amountType;
        @JsonProperty("feeType")
        private String feeType;
        @JsonProperty("feeAmountType")
        private String feeAmountType;
    }
}
