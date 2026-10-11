package com.br.unifioragagent.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionProducer {

    public static final String TOPIC = "document-ingestion";
    public static final String DLT_TOPIC = TOPIC + "-dlt"; // ingestões que falharam mesmo depois das novas tentativas

    private final KafkaTemplate<String, String> kafkaTemplate;

    public IngestionProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishIngestionEvent(IngestionEvent event) {
        // payload simples "caminho|nome|descrição"; a descrição vai por último porque é texto livre e pode conter "|"
        kafkaTemplate.send(TOPIC, event.fileName(), event.filePath() + "|" + event.fileName() + "|" + event.description());
    }
}
