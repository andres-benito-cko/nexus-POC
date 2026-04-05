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
public class GetFieldMappingsHandler implements ToolHandler {

    private final String mappingContent;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetFieldMappingsHandler() {
        this.mappingContent = loadResource("domain/le_nexus_mapping.md");
    }

    @Override
    public String name() {
        return "get_field_mappings";
    }

    @Override
    public String description() {
        return "Returns the LE-to-Nexus field mapping rules, including pillar priority order and "
                + "source fields for each Nexus output field.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        return mappingContent;
    }

    private String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Resource not available: " + path;
        }
    }
}
