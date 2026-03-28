package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateMachineConfig {

    /** Named states and their associated statuses. */
    @JsonProperty("states")
    private Map<String, StateConfig> states;

    /** Ordered transition rules; first match wins. */
    @JsonProperty("transitions")
    private List<TransitionRule> transitions;
}
