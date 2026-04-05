package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetExamplesHandler implements ToolHandler {

    private final Map<String, String> examples = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public GetExamplesHandler() {
        loadExamples();
    }

    @Override
    public String name() {
        return "get_examples";
    }

    @Override
    public String description() {
        return "Returns complete LE-to-Nexus worked examples. Filter by product_type and "
                + "optionally transaction_type to get relevant examples. Files are named "
                + "NN_<product>_<type>_<variant>.json — matching is by substring.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode pt = props.putObject("product_type");
        pt.put("type", "string");
        pt.put("description", "Filter to examples for this product type (e.g. ACQUIRING, PAYOUT)");

        ObjectNode tt = props.putObject("transaction_type");
        tt.put("type", "string");
        tt.put("description", "Further filter by transaction type (e.g. CAPTURE, REFUND)");

        schema.putArray("required").add("product_type");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String productType = input.has("product_type") ? input.get("product_type").asText().toLowerCase() : "";
        String txnType = input.has("transaction_type") ? input.get("transaction_type").asText().toLowerCase() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Matching Examples\n\n");

        int count = 0;
        for (Map.Entry<String, String> entry : examples.entrySet()) {
            String filename = entry.getKey().toLowerCase();
            boolean matches = productType.isEmpty() || filename.contains(productType);
            if (matches && !txnType.isEmpty()) {
                matches = filename.contains(txnType);
            }
            if (matches) {
                sb.append("### ").append(entry.getKey()).append("\n\n");
                sb.append("```json\n").append(entry.getValue()).append("\n```\n\n");
                count++;
                if (count >= 2) break;
            }
        }

        if (count == 0) {
            sb.append("No examples found matching product_type=").append(productType);
            if (!txnType.isEmpty()) sb.append(", transaction_type=").append(txnType);
            sb.append(". Available examples:\n");
            examples.keySet().forEach(k -> sb.append("- ").append(k).append("\n"));
        }

        return sb.toString();
    }

    private void loadExamples() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:domain/examples/*.json");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    try (InputStream is = resource.getInputStream()) {
                        examples.put(filename, new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException e) {
            // Examples not available
        }
    }
}
