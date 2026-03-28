package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a single leg within a transaction template.
 *
 * <p>{@code amount}, {@code status}, {@code fees}, and {@code rolling_reserve}
 * may each be a plain String literal, a {@link ResolverExpression} ({@code $resolve}),
 * or a {@link WhenExpression} ({@code $when}) depending on the YAML shape — they are
 * deserialized as {@link Object} and interpreted at runtime by
 * {@code ExpressionEvaluator}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegConfig {

    @JsonProperty("type")
    private String type;

    /** Optional SpEL condition; leg is skipped when it evaluates to false. */
    @JsonProperty("when")
    private String when;

    @JsonProperty("from_party_type")
    private String fromPartyType;

    @JsonProperty("to_party_type")
    private String toPartyType;

    /** {@link ResolverExpression} or String literal. */
    @JsonProperty("amount")
    private ResolverExpression amount;

    /**
     * String literal status or {@link WhenExpression}.
     * Stored as Object because the YAML value can be either a plain string
     * or a mapping with a {@code $when} key.
     */
    @JsonProperty("status")
    @JsonDeserialize(using = StatusExpressionDeserializer.class)
    private Object status;

    /** Optional {@link ResolverExpression}. */
    @JsonProperty("fees")
    private ResolverExpression fees;

    /** Optional {@link ResolverExpression}. */
    @JsonProperty("rolling_reserve")
    private ResolverExpression rollingReserve;
}
