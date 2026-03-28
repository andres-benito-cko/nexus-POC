package com.checkout.nexus.transformer.engine.context;

import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import com.checkout.nexus.transformer.model.le.CashEvent;
import com.checkout.nexus.transformer.model.le.CosEvent;
import com.checkout.nexus.transformer.model.le.GatewayEvent;
import com.checkout.nexus.transformer.model.le.LeLinkedTransaction;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * Wraps a raw {@link LeLinkedTransaction} and exposes typed pillar accessors
 * and dot-path navigation via {@link #get(String)}.
 */
public class LeContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LeLinkedTransaction raw;

    public LeContext(LeLinkedTransaction raw) {
        this.raw = raw;
    }

    // ------------------------------------------------------------------ hasPillar

    public boolean hasPillar(String pillar) {
        return switch (pillar) {
            case "GATEWAY" -> raw.getGatewayEvents() != null && !raw.getGatewayEvents().isEmpty();
            case "FIAPI"   -> raw.getBalancesChangedEvents() != null && !raw.getBalancesChangedEvents().isEmpty();
            case "COS"     -> raw.getCosEvents() != null && !raw.getCosEvents().isEmpty();
            case "SD"      -> raw.getSchemeSettlementEvents() != null && !raw.getSchemeSettlementEvents().isEmpty();
            case "CASH"    -> raw.getCashEvents() != null && !raw.getCashEvents().isEmpty();
            default        -> false;
        };
    }

    // ------------------------------------------------------------------ typed accessors

    public GatewayEvent getGateway() {
        List<GatewayEvent> events = raw.getGatewayEvents();
        return (events != null && !events.isEmpty()) ? events.get(0) : null;
    }

    public BalancesChangedEvent getFiapi() {
        List<BalancesChangedEvent> events = raw.getBalancesChangedEvents();
        return (events != null && !events.isEmpty()) ? events.get(0) : null;
    }

    public SchemeSettlementEvent getSd() {
        List<SchemeSettlementEvent> events = raw.getSchemeSettlementEvents();
        return (events != null && !events.isEmpty()) ? events.get(0) : null;
    }

    public CashEvent getCash() {
        List<CashEvent> events = raw.getCashEvents();
        return (events != null && !events.isEmpty()) ? events.get(0) : null;
    }

    /** Returns ALL COS events — never null, may be empty. */
    public List<CosEvent> getAllCos() {
        List<CosEvent> events = raw.getCosEvents();
        return (events != null) ? events : Collections.emptyList();
    }

    /** Returns the first COS event, or null when absent. */
    public CosEvent getCos() {
        List<CosEvent> events = raw.getCosEvents();
        return (events != null && !events.isEmpty()) ? events.get(0) : null;
    }

    /** Returns the original {@link LeLinkedTransaction}. */
    public LeLinkedTransaction getRaw() {
        return raw;
    }

    // ------------------------------------------------------------------ dot-path navigation

    /**
     * Resolves a dot-separated path like {@code "SD.metadata.scheme"} against
     * the first event of the named pillar.
     *
     * <p>The first segment selects the pillar (GATEWAY / FIAPI / COS / SD / CASH).
     * Subsequent segments are Jackson {@code @JsonProperty} names navigated on the
     * Jackson {@link JsonNode} representation of the event object.
     *
     * @return the resolved value as a String / Number / Boolean, or {@code null}
     *         when the pillar is absent, the path does not exist, or any
     *         intermediate node is missing/null.
     */
    public Object get(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        int dot = path.indexOf('.');
        if (dot < 0) {
            // Only pillar name given — not a value path
            return null;
        }

        String pillar = path.substring(0, dot);
        String fieldPath = path.substring(dot + 1);

        Object pillarEvent = firstEventFor(pillar);
        if (pillarEvent == null) {
            return null;
        }

        // Convert to JsonNode tree so we can navigate via @JsonProperty names
        JsonNode root;
        try {
            root = MAPPER.valueToTree(pillarEvent);
        } catch (Exception e) {
            return null;
        }

        return navigatePath(root, fieldPath);
    }

    // ------------------------------------------------------------------ helpers

    private Object firstEventFor(String pillar) {
        return switch (pillar) {
            case "GATEWAY" -> getGateway();
            case "FIAPI"   -> getFiapi();
            case "COS"     -> getCos();
            case "SD"      -> getSd();
            case "CASH"    -> getCash();
            default        -> null;
        };
    }

    private Object navigatePath(JsonNode node, String fieldPath) {
        String[] segments = fieldPath.split("\\.");
        JsonNode current = node;
        for (String segment : segments) {
            if (current == null || current.isNull() || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }

        if (current == null || current.isNull()) {
            return null;
        }

        // Extract primitives; return null for complex nodes
        if (current.isTextual()) {
            return current.asText();
        }
        if (current.isInt()) {
            return current.asInt();
        }
        if (current.isLong()) {
            return current.asLong();
        }
        if (current.isDouble() || current.isFloat()) {
            return current.asDouble();
        }
        if (current.isBoolean()) {
            return current.asBoolean();
        }
        if (current.isNumber()) {
            return current.numberValue();
        }

        // Complex node (object/array) — return null
        return null;
    }
}
