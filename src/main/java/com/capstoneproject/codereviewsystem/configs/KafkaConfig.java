package com.capstoneproject.codereviewsystem.configs;

import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewReadyEvent;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewSubmittedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic reviewSubmittedTopic() {
        return TopicBuilder.name(KafkaTopics.REVIEW_SUBMITTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reviewCleanTopic() {
        return TopicBuilder.name(KafkaTopics.REVIEW_CLEAN).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reviewReadyTopic() {
        return TopicBuilder.name(KafkaTopics.REVIEW_READY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reviewFailedTopic() {
        return TopicBuilder.name("review.failed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {

                    System.out.println(
                            "DLQ => " + record.topic());

                    return new TopicPartition(
                            "review.failed",
                            record.partition());
                });

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(2000L, 3));
    }

    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.capstoneproject.codereviewsystem.kafka.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    @Bean
    public ConsumerFactory<String, ReviewSubmittedEvent> submittedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerProps("security-scan-group"),
                new StringDeserializer(),
                new JsonDeserializer<>(ReviewSubmittedEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedEvent> submittedKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedEvent>();
        f.setConsumerFactory(submittedConsumerFactory());
        f.setConcurrency(3);
        f.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate()));
        return f;
    }

    @Bean
    public ConsumerFactory<String, ReviewSubmittedEvent> cleanConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerProps("pipeline-group"),
                new StringDeserializer(),
                new JsonDeserializer<>(ReviewSubmittedEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedEvent> cleanKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedEvent>();
        f.setConsumerFactory(cleanConsumerFactory());
        f.setConcurrency(3);
        f.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate()));
        return f;
    }

    @Bean
    public ConsumerFactory<String, ReviewReadyEvent> readyConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerProps("ai-review-group"),
                new StringDeserializer(),
                new JsonDeserializer<>(ReviewReadyEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewReadyEvent> readyKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, ReviewReadyEvent>();
        f.setConsumerFactory(readyConsumerFactory());
        f.setConcurrency(3);
        f.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate()));
        return f;
    }
}
