package com.checkout.nexus.transformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fee {

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

    @JsonProperty("passthrough")
    private Boolean passthrough;

    @JsonProperty("fee_amount_type")
    private String feeAmountType;

    @JsonProperty("tax_amount")
    private Double taxAmount;

    @JsonProperty("tax_currency")
    private String taxCurrency;
}
