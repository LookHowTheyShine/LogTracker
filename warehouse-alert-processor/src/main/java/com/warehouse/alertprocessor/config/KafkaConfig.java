package com.warehouse.alertprocessor.config;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * Consumer factory typed to WarehouseErrorEvent.
     * setUseTypeHeaders(false) lets us consume messages produced without
     * Spring Kafka's __TypeId__ header (e.g. manual test messages, k6 load test).
     */
    @Bean
    public ConsumerFactory<String, WarehouseErrorEvent> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<WarehouseErrorEvent> deserializer =
                new JsonDeserializer<>(WarehouseErrorEvent.class);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("com.warehouse.alertprocessor.model");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Container factory — MANUAL_IMMEDIATE ack so we only commit after
     * successful processing (or explicit nack for retry/DLQ in Step 7).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, WarehouseErrorEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, WarehouseErrorEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, WarehouseErrorEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        return factory;
    }
}
