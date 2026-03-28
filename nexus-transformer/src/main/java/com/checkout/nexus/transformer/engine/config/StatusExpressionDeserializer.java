package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Custom deserializer for the {@code status} field in {@link LegConfig}.
 *
 * <p>The YAML value can be either:
 * <ul>
 *   <li>A plain string scalar (e.g. {@code ACTUAL}) — returned as a {@link String}.</li>
 *   <li>A mapping with a {@code $when} key — deserialized as a {@link WhenExpression}.</li>
 * </ul>
 */
public class StatusExpressionDeserializer extends StdDeserializer<Object> {

    public StatusExpressionDeserializer() {
        super(Object.class);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject() && node.has("$when")) {
            return mapper.treeToValue(node, WhenExpression.class);
        }
        // Fallback: return raw text representation
        return node.asText();
    }
}
