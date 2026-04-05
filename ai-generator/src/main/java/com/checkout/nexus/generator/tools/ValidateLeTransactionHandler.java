package com.checkout.nexus.generator.tools;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ValidateLeTransactionHandler implements ToolHandler {

    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public ValidateLeTransactionHandler(GeneratorConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return "validate_le_transaction";
    }

    @Override
    public String description() {
        return "Validates an LE transaction by running it through the NexusEngine dry-run. "
                + "Returns the transformation result with success/failure and any errors. "
                + "The input must be a complete LE transaction JSON string.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode le = props.putObject("le_transaction");
        le.put("type", "string");
        le.put("description", "Complete LE transaction as a JSON string");
        schema.putArray("required").add("le_transaction");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String leJson = input.path("le_transaction").asText();

        try {
            String url = config.getTransformer().getUrl() + "/transform/test";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(leJson))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                ObjectNode error = mapper.createObjectNode();
                error.put("error", "Transformer returned HTTP " + response.statusCode());
                error.put("body", response.body());
                return error.toString();
            }
        } catch (Exception e) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Failed to reach transformer: " + e.getMessage());
            error.put("transformer_url", config.getTransformer().getUrl());
            return error.toString();
        }
    }
}
