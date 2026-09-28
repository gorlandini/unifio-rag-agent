package com.br.unifioragagent.controller;

import com.br.unifioragagent.messaging.IngestionEvent;
import com.br.unifioragagent.messaging.IngestionProducer;
import com.br.unifioragagent.service.DocumentIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final Path uploadDir;

    public DocumentController(IngestionProducer producer,
                              DocumentIngestionService ingestionService,
                              @Value("${app.upload-dir:uploads}") String uploadDir) throws IOException {
        this.producer = producer;
        this.ingestionService = ingestionService;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(this.uploadDir);
    }

    public record UploadResult(String fileName, int pages, String status) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<UploadResult> upload(@RequestParam("files") List<MultipartFile> files) {
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
                producer.publishIngestionEvent(new IngestionEvent(destino.toString(), fileName));
                results.add(new UploadResult(fileName, 0, "enfileirado"));
            } catch (IOException e) {
                // um arquivo com problema não derruba os demais
                results.add(new UploadResult(fileName, 0, "erro: " + e.getMessage()));
            }
        }
        return results;
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> delete(@PathVariable String fileName) {
        ingestionService.deleteByFileName(fileName);
        return ResponseEntity.noContent().build();
    }
}
