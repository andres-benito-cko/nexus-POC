package com.checkout.nexus.generator.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolHandler {
    String name();
    String description();
    JsonNode inputSchema();
    String execute(JsonNode input);
}
