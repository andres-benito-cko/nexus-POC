package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.tools.ToolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolExecutor {

    private final Map<String, ToolHandler> handlers;

    public ToolExecutor(List<ToolHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ToolHandler::name, Function.identity()));
    }

    public String execute(String toolName, JsonNode input) {
        ToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            return "Unknown tool: " + toolName + ". Available tools: " + handlers.keySet();
        }

        log.info("Executing tool: {} with input: {}", toolName, input);
        try {
            String result = handler.execute(input);
            log.debug("Tool {} returned {} chars", toolName, result.length());
            return result;
        } catch (Exception e) {
            log.error("Tool {} failed: {}", toolName, e.getMessage(), e);
            return "Tool execution error: " + e.getMessage();
        }
    }

    public List<ToolHandler> getHandlers() {
        return List.copyOf(handlers.values());
    }
}
