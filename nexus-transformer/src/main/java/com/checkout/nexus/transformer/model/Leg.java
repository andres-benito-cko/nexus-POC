package com.checkout.nexus.transformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Leg {

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

    @JsonProperty("reserve_amount")
    private Double reserveAmount;

    @JsonProperty("reserve_currency")
    private String reserveCurrency;

    @JsonProperty("fees")
    private List<Fee> fees;
}
