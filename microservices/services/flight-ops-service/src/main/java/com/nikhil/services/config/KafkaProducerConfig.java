package com.nikhil.services.config;

import com.nikhil.common_lib.event.FlightInstanceCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/*
 * Kafka producer wiring for flight-ops-service (producer-only; no consumer in this service).
 *
 * FlightInstanceEventProducer uses the KafkaTemplate bean to publish FlightInstanceCreatedEvent
 * to topic "flight-instance-created" after a schedule or instance is persisted. seat-service
 * consumes the event to provision seat maps — decoupling seat setup from the HTTP create flow.
 * Bootstrap servers come from spring.kafka.bootstrap-servers (Config Server / local yaml).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Producer factory with String keys and Jackson JSON values for FlightInstanceCreatedEvent. */
    @Bean
    public ProducerFactory<String, FlightInstanceCreatedEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /** Injected into FlightInstanceEventProducer to send flight-instance-created messages. */
    @Bean
    public KafkaTemplate<String, FlightInstanceCreatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
