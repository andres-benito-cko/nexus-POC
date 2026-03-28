package com.checkout.nexus.rulesengine.config;

import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    // Default factory for nexus.ledger.entries — produced by rules-engine itself, consistent type headers.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LedgerEntryMessage> kafkaListenerContainerFactory() {
        JsonDeserializer<LedgerEntryMessage> deserializer = new JsonDeserializer<>(LedgerEntryMessage.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        ConsumerFactory<String, LedgerEntryMessage> cf =
                new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, LedgerEntryMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        return factory;
    }

    // Factory for nexus.blocks — type headers disabled by nexus-transformer producer.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NexusBlock> nexusContainerFactory() {
        JsonDeserializer<NexusBlock> deserializer = new JsonDeserializer<>(NexusBlock.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        ConsumerFactory<String, NexusBlock> cf =
                new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, NexusBlock> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        return factory;
    }

    // Factory for le.linked.transactions — type headers disabled by le-simulator producer.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Map> leEventContainerFactory() {
        JsonDeserializer<Map> deserializer = new JsonDeserializer<>(Map.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        ConsumerFactory<String, Map> cf =
                new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, Map> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        return factory;
    }
}
