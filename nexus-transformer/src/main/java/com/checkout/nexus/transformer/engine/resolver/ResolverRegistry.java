package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates all {@link FieldResolver} beans and dispatches resolver calls by name.
 */
@Slf4j
@Component
public class ResolverRegistry {

    private final Map<String, FieldResolver> resolversByName;

    public ResolverRegistry(List<FieldResolver> resolvers) {
        this.resolversByName = resolvers.stream()
                .collect(Collectors.toMap(FieldResolver::name, r -> r));
        log.info("ResolverRegistry initialised with {} resolvers: {}", resolvers.size(), resolversByName.keySet());
    }

    public List<String> getResolverNames() {
        return new ArrayList<>(resolversByName.keySet());
    }

    /**
     * Invokes the named resolver.
     *
     * @param name   resolver name (e.g. "SETTLEMENT_AMOUNT")
     * @param ctx    current LE context
     * @param params optional resolver params from YAML config
     * @return resolved value or null when the resolver is not found / returns null
     */
    public Object resolve(String name, LeContext ctx, Map<String, Object> params) {
        FieldResolver resolver = resolversByName.get(name);
        if (resolver == null) {
            log.warn("No resolver registered for name '{}'; returning null", name);
            return null;
        }
        try {
            return resolver.resolve(ctx, params);
        } catch (Exception e) {
            log.warn("Resolver '{}' threw an exception; returning null. Error: {}", name, e.getMessage());
            return null;
        }
    }
}
