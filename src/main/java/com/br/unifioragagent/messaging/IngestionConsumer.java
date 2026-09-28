package com.br.unifioragagent.messaging;



import com.br.unifioragagent.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class IngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestionConsumer.class);

    private final DocumentIngestionService ingestionService;

    public IngestionConsumer(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = IngestionProducer.TOPIC)
    public void onMessage(String payload) throws IOException {
        String[] partes = payload.split("\\|", 2);
        Path path = Path.of(partes[0]);
        String fileName = partes[1];

        // mesma lógica do Marco 4, agora fora da requisição HTTP
        int paginas = ingestionService.ingestPdf(Files.readAllBytes(path), fileName);
        log.info("Ingestão concluída: {} ({} páginas)", fileName, paginas);
    }
}