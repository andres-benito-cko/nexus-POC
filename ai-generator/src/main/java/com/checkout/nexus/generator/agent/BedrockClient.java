package com.checkout.nexus.generator.agent;

import com.checkout.nexus.generator.config.GeneratorConfig;
import com.checkout.nexus.generator.tools.ToolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

@Slf4j
@Component
public class BedrockClient {

    private final GeneratorConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private BedrockRuntimeClient client;

    public BedrockClient(GeneratorConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(config.getBedrock().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    public ConverseResponse converse(
            String systemPrompt,
            List<Message> messages,
            List<ToolHandler> tools
    ) {
        List<Tool> bedrockTools = tools.stream()
                .map(this::toBedrockTool)
                .toList();

        ConverseRequest.Builder request = ConverseRequest.builder()
                .modelId(config.getBedrock().getModelId())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(messages)
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(4096)
                        .temperature(0.0f)
                        .build());

        if (!bedrockTools.isEmpty()) {
            request.toolConfig(ToolConfiguration.builder()
                    .tools(bedrockTools)
                    .build());
        }

        return client.converse(request.build());
    }

    private Tool toBedrockTool(ToolHandler handler) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(handler.name())
                        .description(handler.description())
                        .inputSchema(ToolInputSchema.builder()
                                .json(toDocument(handler.inputSchema()))
                                .build())
                        .build())
                .build();
    }

    private software.amazon.awssdk.core.document.Document toDocument(JsonNode jsonNode) {
        try {
            String json = mapper.writeValueAsString(jsonNode);
            return software.amazon.awssdk.core.document.Document.fromString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert tool schema to Document", e);
        }
    }
}
