package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateConfig {

    @JsonProperty("block_status")
    private String blockStatus;

    @JsonProperty("transaction_status")
    private String transactionStatus;
}
