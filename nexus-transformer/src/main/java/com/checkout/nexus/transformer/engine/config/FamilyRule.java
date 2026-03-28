package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single family-classification rule.
 *
 * <p>Rules have either a {@code when} condition (SpEL expression evaluated at runtime)
 * or a {@code default} flag indicating the fallback result.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FamilyRule {

    /** SpEL expression that must evaluate to {@code true} for this rule to match. Null for default rules. */
    @JsonProperty("when")
    private String when;

    /** The trade-family result string (e.g. "CASH", "PAYOUT", "ACQUIRING"). */
    @JsonProperty("result")
    private String result;

    /**
     * When set as a non-null value this is a default/fallback rule
     * (loaded from the {@code default:} YAML key).
     */
    @JsonProperty("default")
    private String defaultResult;

    /** Convenience: true if this rule is a default (no when condition). */
    public boolean isDefaultRule() {
        return defaultResult != null;
    }

    /** Returns the effective result regardless of whether this is a conditional or default rule. */
    public String effectiveResult() {
        return (result != null) ? result : defaultResult;
    }
}
