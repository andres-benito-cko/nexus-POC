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
public class Trade {

    @JsonProperty("trade_id")
    private String tradeId;

    @JsonProperty("trade_family")
    private String tradeFamily;

    @JsonProperty("trade_type")
    private String tradeType;

    @JsonProperty("trade_status")
    private String tradeStatus;

    @JsonProperty("trade_amount")
    private double tradeAmount;

    @JsonProperty("trade_currency")
    private String tradeCurrency;

    @JsonProperty("trade_date")
    private String tradeDate;

    @JsonProperty("metadata")
    private TradeMetadata metadata;

    @JsonProperty("legs")
    private List<Leg> legs;
}
