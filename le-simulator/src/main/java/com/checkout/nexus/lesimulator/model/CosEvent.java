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
public class CosEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("payload")
    private CosPayload payload;

    @JsonProperty("metadata")
    private CosMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CosPayload {

        @JsonProperty("fee")
        private AmountValue fee;

        @JsonProperty("vat")
        private AmountValue vat;

        @JsonProperty("isPredicted")
        private boolean isPredicted;

        @JsonProperty("feeType")
        private String feeType;

        @JsonProperty("feeSubType")
        private String feeSubType;

        @JsonProperty("direction")
        private String direction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CosMetadata {

        @JsonProperty("acquirerName")
        private String acquirerName;

        @JsonProperty("acquirerCountry")
        private String acquirerCountry;

        @JsonProperty("acquirerCompanyCkoLegalEntityCode")
        private String acquirerCompanyCkoLegalEntityCode;

        @JsonProperty("paymentMethod")
        private String paymentMethod;

        @JsonProperty("invoiceNumber")
        private String invoiceNumber;

        @JsonProperty("invoiceDate")
        private String invoiceDate;

        @JsonProperty("schemePartnerIdentifier")
        private String schemePartnerIdentifier;

        @JsonProperty("reconciliationReference")
        private String reconciliationReference;

        @JsonProperty("isNetSettled")
        private Boolean isNetSettled;

        @JsonProperty("entityId")
        private String entityId;

        @JsonProperty("entitySubId")
        private String entitySubId;
    }
}
