package com.checkout.nexus.lesimulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("standardPayload")
    private CashStandardPayload standardPayload;

    @JsonProperty("standardMetadata")
    private CashStandardMetadata standardMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CashStandardPayload {

        @JsonProperty("amount")
        private AmountValue amount;

        @JsonProperty("direction")
        private String direction;

        @JsonProperty("bankFeeAmount")
        private AmountValue bankFeeAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CashStandardMetadata {

        @JsonProperty("scheme")
        private String scheme;

        @JsonProperty("valueDate")
        private String valueDate;

        @JsonProperty("legalEntity")
        private String legalEntity;

        @JsonProperty("actionType")
        private String actionType;

        @JsonProperty("entityId")
        private String entityId;

        @JsonProperty("entitySubId")
        private String entitySubId;

        @JsonProperty("currencyAccountId")
        private String currencyAccountId;

        @JsonProperty("payoutId")
        private String payoutId;

        @JsonProperty("billingDescriptor")
        private String billingDescriptor;

        @JsonProperty("invoiceNumber")
        private String invoiceNumber;

        @JsonProperty("cashBatchId")
        private String cashBatchId;
    }
}
