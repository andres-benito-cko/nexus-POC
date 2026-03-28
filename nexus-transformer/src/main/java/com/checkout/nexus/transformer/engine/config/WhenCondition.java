package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single branch within a {@link WhenExpression}.
 *
 * <p>Either an {@code if}/{@code then} pair, or a standalone {@code else} entry.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhenCondition {

    /** SpEL condition string; null for else branches. */
    @JsonProperty("if")
    private String ifExpr;

    /** Result when condition matches. */
    @JsonProperty("then")
    private String thenResult;

    /** Result for the else branch (no condition). */
    @JsonProperty("else")
    private String elseResult;

    public boolean isElseBranch() {
        return ifExpr == null && elseResult != null;
    }
}
