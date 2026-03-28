package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A state-machine transition rule.
 *
 * <p>Either has a {@code when} condition (SpEL) + {@code to} state name,
 * or is a default rule ({@code default} key) that always fires.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransitionRule {

    /** Target state name when condition matches. */
    @JsonProperty("to")
    private String to;

    /** SpEL condition; null for default rules. */
    @JsonProperty("when")
    private String when;

    /**
     * Default state name loaded from the {@code default:} YAML key.
     * Mutually exclusive with {@code when}.
     */
    @JsonProperty("default")
    private String defaultState;

    public boolean isDefaultRule() {
        return defaultState != null;
    }

    /** Effective target state name, regardless of rule kind. */
    public String effectiveTarget() {
        return (to != null) ? to : defaultState;
    }
}
