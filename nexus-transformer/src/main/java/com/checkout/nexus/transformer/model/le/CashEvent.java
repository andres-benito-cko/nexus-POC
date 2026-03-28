package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("standardPayload")
    private StandardPayload standardPayload;

    @JsonProperty("standardMetadata")
    private StandardMetadata standardMetadata;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandardPayload {
        @JsonProperty("amount")
        private AmountValue amount;
        @JsonProperty("direction")
        private String direction;
        @JsonProperty("bankFeeAmount")
        private AmountValue bankFeeAmount;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandardMetadata {
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
