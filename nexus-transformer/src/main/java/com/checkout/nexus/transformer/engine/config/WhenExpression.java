package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a {@code $when} expression in the YAML config:
 *
 * <pre>
 * status:
 *   $when:
 *     - if: "pillars.contains('SD')"
 *       then: ACTUAL
 *     - else: PREDICTED
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhenExpression {

    @JsonProperty("$when")
    private List<WhenCondition> conditions;
}
