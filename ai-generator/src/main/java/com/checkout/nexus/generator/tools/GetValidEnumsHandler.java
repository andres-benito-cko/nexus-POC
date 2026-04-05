package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class GetValidEnumsHandler implements ToolHandler {

    private final String combinationsContent;
    private final JsonNode combinationsJson;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetValidEnumsHandler() {
        this.combinationsContent = loadResource("domain/valid_combinations.json");
        JsonNode parsed;
        try {
            parsed = mapper.readTree(this.combinationsContent);
        } catch (Exception e) {
            parsed = mapper.createObjectNode();
        }
        this.combinationsJson = parsed;
    }

    @Override
    public String name() {
        return "get_valid_enums";
    }

    @Override
    public String description() {
        return "Returns valid product_type x transaction_type x transaction_status combinations, "
                + "leg composition rules, and all enum values. Optionally filter by product_type.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter to combinations for this product type");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText() : null;

        if (productType == null) {
            return combinationsContent;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Valid combinations for ").append(productType).append("\n\n");

        JsonNode combos = combinationsJson.path("valid_combinations");
        if (combos.isArray()) {
            for (JsonNode combo : combos) {
                if (productType.equals(combo.path("family").asText())) {
                    sb.append("- ").append(combo.path("type").asText())
                            .append(": ").append(combo.path("statuses")).append("\n");
                }
            }
        }

        sb.append("\n## Leg composition rules for ").append(productType).append("\n\n");
        JsonNode legRules = combinationsJson.path("leg_composition_rules");
        if (legRules.isArray()) {
            for (JsonNode rule : legRules) {
                if (productType.equals(rule.path("family").asText())) {
                    sb.append("- ").append(rule.path("type").asText())
                            .append(": ").append(rule.path("legs")).append("\n");
                }
            }
        }

        sb.append("\n## All enums\n\n");
        sb.append(combinationsJson.path("enums").toPrettyString());

        return sb.toString();
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{}";
        }
    }
}
