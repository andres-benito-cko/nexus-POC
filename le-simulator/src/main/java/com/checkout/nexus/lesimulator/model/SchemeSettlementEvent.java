package com.checkout.nexus.lesimulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class SchemeSettlementEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("payload")
    private SdPayload payload;

    @JsonProperty("metadata")
    private SdMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SdPayload {

        @JsonProperty("settlementAmount")
        private SignedAmount settlementAmount;

        @JsonProperty("processingAmount")
        private SignedAmount processingAmount;

        @JsonProperty("fees")
        private List<SdFee> fees;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignedAmount {

        @JsonProperty("money")
        private AmountValue money;

        @JsonProperty("sign")
        private String sign;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SdFee {

        @JsonProperty("type")
        private String type;

        @JsonProperty("roundedAmount")
        private double roundedAmount;

        @JsonProperty("currencyCode")
        private String currencyCode;

        @JsonProperty("sign")
        private String sign;

        @JsonProperty("taxAmount")
        private double taxAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SdMetadata {

        @JsonProperty("scheme")
        private String scheme;

        @JsonProperty("transactionType")
        private String transactionType;

        @JsonProperty("expectedValueDate")
        private String expectedValueDate;

        @JsonProperty("centralProcessingDate")
        private String centralProcessingDate;

        @JsonProperty("settlementServiceName")
        private String settlementServiceName;

        @JsonProperty("settlementCountryCode")
        private String settlementCountryCode;

        @JsonProperty("acquirerCompanyCkoLegalEntityCode")
        private String acquirerCompanyCkoLegalEntityCode;

        @JsonProperty("settlementCompanyCkoLegalEntityCode")
        private String settlementCompanyCkoLegalEntityCode;

        @JsonProperty("acquirerName")
        private String acquirerName;

        @JsonProperty("acquirerCountry")
        private String acquirerCountry;

        @JsonProperty("entityId")
        private String entityId;

        @JsonProperty("entitySubId")
        private String entitySubId;

        @JsonProperty("clientId")
        private String clientId;
    }
}
