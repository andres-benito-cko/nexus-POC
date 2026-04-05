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
public class GetSchemaHandler implements ToolHandler {

    private final String schemaContent;
    private final String pillarStructures;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetSchemaHandler() {
        this.schemaContent = loadResource("domain/nexus.schema.json");
        this.pillarStructures = loadResource("domain/per_pillar_structures.md");
    }

    @Override
    public String name() {
        return "get_schema";
    }

    @Override
    public String description() {
        return "Returns the Nexus transaction JSON schema definition and LE pillar structures. "
                + "Optionally filter by product_type to get relevant subset.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter schema context to this product type (e.g. ACQUIRING, PAYOUT)");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("## Nexus Schema\n\n");
        sb.append(schemaContent);

        if (productType != null) {
            sb.append("\n\n## Pillar Structures (filtered for ").append(productType).append(")\n\n");
        } else {
            sb.append("\n\n## Pillar Structures\n\n");
        }
        sb.append(pillarStructures);

        return sb.toString();
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Resource not available: " + path;
        }
    }
}
