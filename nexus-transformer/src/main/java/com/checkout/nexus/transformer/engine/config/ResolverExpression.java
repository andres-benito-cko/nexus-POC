package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a {@code $resolve} expression in the YAML config:
 *
 * <pre>
 * amount:
 *   $resolve: SETTLEMENT_AMOUNT
 *   params:
 *     source_priority: [SD, COS]
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResolverExpression {

    /** Name of the resolver to invoke (e.g. "SETTLEMENT_AMOUNT"). */
    @JsonProperty("$resolve")
    private String resolverName;

    /** Optional parameters passed to the resolver. */
    @JsonProperty("params")
    private Map<String, Object> params;
}
