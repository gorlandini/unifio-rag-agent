package com.br.unifioragagent.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionProducer {

    public static final String TOPIC = "document-ingestion";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public IngestionProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishIngestionEvent(IngestionEvent event) {
        // payload simples "caminho|nome"; troque por JSON quando precisar de mais campos
        kafkaTemplate.send(TOPIC, event.fileName(), event.filePath() + "|" + event.fileName());
    }
}
