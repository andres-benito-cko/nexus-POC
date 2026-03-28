package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemeSettlementEvent {

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
        @JsonProperty("settlementAmount")
        private SignedAmount settlementAmount;
        @JsonProperty("processingAmount")
        private SignedAmount processingAmount;
        @JsonProperty("fees")
        private List<SdFee> fees;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignedAmount {
        @JsonProperty("money")
        private AmountValue money;
        @JsonProperty("sign")
        private String sign;
    }

    @Data
    @NoArgsConstructor
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
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
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
