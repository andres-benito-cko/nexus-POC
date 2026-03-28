package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayEvent {

    @JsonProperty("envelope")
    private EventEnvelope envelope;

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("processedOn")
    private String processedOn;

    @JsonProperty("amount")
    private AmountValue amount;

    @JsonProperty("acquirerName")
    private String acquirerName;

    @JsonProperty("acquirerCountry")
    private String acquirerCountry;

    @JsonProperty("cashBatchId")
    private String cashBatchId;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("responseCode")
    private String responseCode;
}
