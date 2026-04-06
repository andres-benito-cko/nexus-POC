package com.checkout.nexus.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "generator")
public class GeneratorConfig {

    private Bedrock bedrock = new Bedrock();
    private Transformer transformer = new Transformer();
    private Agent agent = new Agent();

    @Data
    public static class Bedrock {
        private String region = "eu-west-1";
        private String modelId = "anthropic.claude-sonnet-4-20250514-v1:0";
    }

    @Data
    public static class Transformer {
        private String url = "http://localhost:8082";
    }

    @Data
    public static class Agent {
        private int maxIterations = 3;
        private int timeoutSeconds = 60;
    }
}
