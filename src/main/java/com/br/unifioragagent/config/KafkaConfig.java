package com.br.unifioragagent.config;

import com.br.unifioragagent.messaging.IngestionProducer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    // o Spring Boot aplica este bean a todos os @KafkaListener:
    // falhou → tenta mais 2 vezes (2 s entre elas) → publica a mensagem original no tópico de erros
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(IngestionProducer.DLT_TOPIC, -1)); // -1: o Kafka escolhe a partição
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 2));
    }
}
