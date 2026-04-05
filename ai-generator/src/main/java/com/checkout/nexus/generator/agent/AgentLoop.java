package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.checkout.nexus.generator.model.GenerateResponse;
import com.checkout.nexus.generator.model.ProgressEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentLoop {

    private final BedrockClient bedrockClient;
    private final ToolExecutor toolExecutor;
    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String systemPrompt;

    public AgentLoop(BedrockClient bedrockClient, ToolExecutor toolExecutor, GeneratorConfig config) {
        this.bedrockClient = bedrockClient;
        this.toolExecutor = toolExecutor;
        this.config = config;
        this.systemPrompt = loadSystemPrompt();
    }

    public GenerateResponse run(String userPrompt, Consumer<ProgressEvent> onProgress) {
        onProgress.accept(new ProgressEvent("understanding", "Parsing scenario: " + truncate(userPrompt, 80)));

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build());

        int iteration = 0;
        int maxIterations = config.getAgent().getMaxIterations() * 5;

        while (iteration < maxIterations) {
            iteration++;

            ConverseResponse response;
            try {
                response = bedrockClient.converse(systemPrompt, messages, toolExecutor.getHandlers());
            } catch (Exception e) {
                log.error("Bedrock call failed: {}", e.getMessage(), e);
                return GenerateResponse.builder()
                        .success(false)
                        .errors(List.of("Bedrock error: " + e.getMessage()))
                        .build();
            }

            Message assistantMessage = response.output().message();
            messages.add(assistantMessage);

            if (response.stopReason() == StopReason.TOOL_USE) {
                List<ContentBlock> toolResults = new ArrayList<>();

                for (ContentBlock block : assistantMessage.content()) {
                    if (block.toolUse() != null) {
                        ToolUseBlock toolUse = block.toolUse();
                        String toolName = toolUse.name();

                        onProgress.accept(new ProgressEvent(
                                toolName.contains("validate") ? "validating" : "context",
                                "Calling " + toolName
                        ));

                        JsonNode toolInput;
                        try {
                            String inputJson = toolUse.input().unwrap().toString();
                            toolInput = mapper.readTree(inputJson);
                        } catch (Exception e) {
                            toolInput = mapper.createObjectNode();
                        }

                        String result = toolExecutor.execute(toolName, toolInput);

                        if (toolName.equals("validate_le_transaction")) {
                            try {
                                JsonNode resultJson = mapper.readTree(result);
                                if (resultJson.path("success").asBoolean(false)) {
                                    onProgress.accept(new ProgressEvent("validating", "Validation passed"));
                                } else {
                                    onProgress.accept(new ProgressEvent("correcting", "Validation failed, agent will fix"));
                                }
                            } catch (Exception e) {
                                // Not JSON, continue
                            }
                        }

                        toolResults.add(ContentBlock.builder()
                                .toolResult(ToolResultBlock.builder()
                                        .toolUseId(toolUse.toolUseId())
                                        .content(ToolResultContentBlock.builder()
                                                .text(result)
                                                .build())
                                        .build())
                                .build());
                    }
                }

                messages.add(Message.builder()
                        .role(ConversationRole.USER)
                        .content(toolResults)
                        .build());

            } else if (response.stopReason() == StopReason.END_TURN) {
                return extractResult(assistantMessage, onProgress);
            } else {
                return GenerateResponse.builder()
                        .success(false)
                        .errors(List.of("Unexpected stop reason: " + response.stopReason()))
                        .build();
            }
        }

        return GenerateResponse.builder()
                .success(false)
                .errors(List.of("Agent loop exceeded maximum iterations (" + maxIterations + ")"))
                .build();
    }

    private GenerateResponse extractResult(Message assistantMessage, Consumer<ProgressEvent> onProgress) {
        for (ContentBlock block : assistantMessage.content()) {
            if (block.text() != null) {
                String text = block.text();
                try {
                    JsonNode leTransaction = mapper.readTree(text);
                    onProgress.accept(new ProgressEvent("complete", "LE transaction generated"));
                    return GenerateResponse.builder()
                            .success(true)
                            .leTransaction(leTransaction)
                            .validationPassed(true)
                            .build();
                } catch (Exception e) {
                    String json = extractJsonFromText(text);
                    if (json != null) {
                        try {
                            JsonNode leTransaction = mapper.readTree(json);
                            onProgress.accept(new ProgressEvent("complete", "LE transaction generated"));
                            return GenerateResponse.builder()
                                    .success(true)
                                    .leTransaction(leTransaction)
                                    .validationPassed(true)
                                    .build();
                        } catch (Exception e2) {
                            // Fall through
                        }
                    }
                    return GenerateResponse.builder()
                            .success(false)
                            .errors(List.of("Agent returned non-JSON response: " + truncate(text, 200)))
                            .build();
                }
            }
        }

        return GenerateResponse.builder()
                .success(false)
                .errors(List.of("Agent returned empty response"))
                .build();
    }

    private String extractJsonFromText(String text) {
        int start = text.indexOf("```json");
        if (start >= 0) {
            start = text.indexOf('\n', start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) return text.substring(start, end).trim();
        }
        start = text.indexOf("```");
        if (start >= 0) {
            start = text.indexOf('\n', start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) return text.substring(start, end).trim();
        }
        start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return null;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String loadSystemPrompt() {
        try (InputStream is = new ClassPathResource("system-prompt.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system-prompt.txt", e);
        }
    }
}
