package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Top-level YAML configuration model for the Nexus Configurable Engine.
 * Loaded from {@code nexus-engine-config.yaml} on the classpath.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NexusEngineConfig {

    @JsonProperty("version")
    private String version;

    @JsonProperty("classification")
    private ClassificationConfig classification;

    @JsonProperty("state_machines")
    private Map<String, StateMachineConfig> stateMachines;

    @JsonProperty("trades")
    private Map<String, Map<String, TradeConfig>> trades;

    @JsonProperty("field_mappings")
    private Map<String, FieldMapping> fieldMappings;

    @JsonProperty("fee_type_mappings")
    private Map<String, Map<String, String>> feeTypeMappings;
}
