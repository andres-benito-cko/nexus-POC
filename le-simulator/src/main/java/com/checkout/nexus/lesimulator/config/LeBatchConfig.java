package com.checkout.nexus.lesimulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "le.kafka")
public class LeBatchConfig {

    private String topicName = "le.linked.transactions";
}
