package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CosEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("payload")
    private Payload payload;

    @JsonProperty("metadata")
    private Metadata metadata;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
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
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
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
