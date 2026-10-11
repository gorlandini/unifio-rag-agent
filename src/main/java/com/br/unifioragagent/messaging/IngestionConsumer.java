package com.br.unifioragagent.messaging;



import com.br.unifioragagent.service.DocumentIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class IngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestionConsumer.class);

    private final DocumentIngestionService ingestionService;
    private final IngestionNotifier notifier;

    public IngestionConsumer(DocumentIngestionService ingestionService, IngestionNotifier notifier) {
        this.ingestionService = ingestionService;
        this.notifier = notifier;
    }

    @KafkaListener(topics = IngestionProducer.TOPIC)
    public void onMessage(String payload) throws IOException {
        String[] partes = payload.split("\\|", 3);
        Path path = Path.of(partes[0]);
        String fileName = partes[1];
        String description = partes.length > 2 ? partes[2] : ""; // mensagens antigas ("caminho|nome") não têm descrição

        // mesma lógica do Marco 4, agora fora da requisição HTTP
        int paginas = ingestionService.ingestPdf(Files.readAllBytes(path), fileName, description);
        log.info("Ingestão concluída: {} ({} páginas)", fileName, paginas);
        notifier.concluido(fileName, paginas);
    }

    // tópico de erros: por enquanto só registra no log qual arquivo falhou e por quê (dá para reprocessar reenviando o PDF)
    @KafkaListener(topics = IngestionProducer.DLT_TOPIC)
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        Header erro = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String motivo = erro == null ? "?" : new String(erro.value(), StandardCharsets.UTF_8);
        log.error("Ingestão falhou após novas tentativas: {} | {}", record.key(), motivo);
        notifier.erro(record.key(), motivo); // a chave da mensagem é o fileName (ver IngestionProducer)
    }
}
