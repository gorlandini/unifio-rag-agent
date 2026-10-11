package com.br.unifioragagent.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// avisa os navegadores conectados (SSE) quando a ingestão de um PDF termina ou falha
@Component
public class IngestionNotifier {

    private static final Logger log = LoggerFactory.getLogger(IngestionNotifier.class);

    public record IngestionNotification(String fileName, String status, int pages, String message) {}

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = sem timeout; o EventSource do navegador reconecta sozinho se cair
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void concluido(String fileName, int pages) {
        send(new IngestionNotification(fileName, "concluido", pages, null));
    }

    public void erro(String fileName, String message) {
        send(new IngestionNotification(fileName, "erro", 0, message));
    }

    private void send(IngestionNotification notification) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ingestion").data(notification));
            } catch (IOException | IllegalStateException e) {
                // aba fechada: a conexão morreu, só descarta
                emitters.remove(emitter);
            }
        }
        log.info("Aviso SSE enviado a {} conexão(ões): {}", emitters.size(), notification);
    }
}
