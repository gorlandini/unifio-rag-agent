package com.br.unifioragagent.controller;

import com.br.unifioragagent.messaging.IngestionEvent;
import com.br.unifioragagent.messaging.IngestionNotifier;
import com.br.unifioragagent.messaging.IngestionProducer;
import com.br.unifioragagent.service.DocumentIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/institutional-documents")
public class DocumentController {

    private final IngestionProducer producer;
    private final DocumentIngestionService ingestionService;
    private final IngestionNotifier notifier;
    private final Path uploadDir;

    public DocumentController(IngestionProducer producer,
                              DocumentIngestionService ingestionService,
                              IngestionNotifier notifier,
                              @Value("${app.upload-dir:uploads}") String uploadDir) throws IOException {
        this.producer = producer;
        this.ingestionService = ingestionService;
        this.notifier = notifier;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(this.uploadDir);
    }

    private static final int MAX_DESCRIPTION = 500; // "breve": vai repetida em todos os trechos do arquivo

    public record UploadResult(String fileName, int pages, String status) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<UploadResult> upload(@RequestParam("files") List<MultipartFile> files,
                                     @RequestParam(value = "description", required = false) String description) {
        // opcional; se vierem vários arquivos na mesma requisição, todos recebem a mesma descrição
        String descricao = description == null ? "" : description.strip();
        if (descricao.length() > MAX_DESCRIPTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "descrição com mais de " + MAX_DESCRIPTION + " caracteres");
        }
        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            String original = Objects.requireNonNullElse(file.getOriginalFilename(), "sem-nome.pdf");
            String fileName = Path.of(original).getFileName().toString(); // evita path traversal

            if (file.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
                results.add(new UploadResult(fileName, 0, "ignorado: não é PDF ou está vazio"));
                continue;
            }

            try (InputStream in = file.getInputStream()) {
                Path destino = uploadDir.resolve(fileName);
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);

                // publica o evento; o IngestionConsumer faz a ingestão
                producer.publishIngestionEvent(new IngestionEvent(destino.toString(), fileName, descricao));
                results.add(new UploadResult(fileName, 0, "enfileirado"));
            } catch (IOException e) {
                // um arquivo com problema não derruba os demais
                results.add(new UploadResult(fileName, 0, "erro: " + e.getMessage()));
            }
        }
        return results;
    }

    @GetMapping
    public List<DocumentIngestionService.DocumentSummary> list() {
        return ingestionService.listDocuments();
    }

    // SSE: o front fica conectado e recebe um evento "ingestion" quando cada PDF termina (ou falha) no consumer
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return notifier.subscribe();
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> delete(@PathVariable String fileName) throws IOException {
        ingestionService.deleteByFileName(fileName);
        // mesmo saneamento do upload: só apaga dentro de uploads/
        Files.deleteIfExists(uploadDir.resolve(Path.of(fileName).getFileName().toString()));
        return ResponseEntity.noContent().build();
    }
}
