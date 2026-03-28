package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;

import java.util.Map;

/**
 * Strategy interface for named value resolvers invoked from {@code $resolve} expressions
 * in the YAML engine config.
 */
public interface FieldResolver {

    /** Unique name matching the {@code $resolve} key in YAML (e.g. {@code "SETTLEMENT_AMOUNT"}). */
    String name();

    /**
     * Resolves a value from the transaction context.
     *
     * @param ctx    the current LE transaction context
     * @param params optional parameters from the YAML config (may be null or empty)
     * @return the resolved value, or {@code null} when not available
     */
    Object resolve(LeContext ctx, Map<String, Object> params);
}
