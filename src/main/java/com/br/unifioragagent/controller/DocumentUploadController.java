package com.br.unifioragagent.controller;



import com.br.unifioragagent.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
public class DocumentUploadController {

    private final DocumentIngestionService ingestionService;

    public DocumentUploadController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public record UploadResult(String fileName, int pages, String status) {}

    @PostMapping("/institutional-documents")
    public List<UploadResult> upload(@RequestParam("files") List<MultipartFile> files) {
        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "sem-nome.pdf");

            if (file.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
                results.add(new UploadResult(fileName, 0, "ignorado: não é PDF ou está vazio"));
                continue;
            }

            try {
                int pages = ingestionService.ingestPdf(file.getBytes(), fileName);
                results.add(new UploadResult(fileName, pages, "ok"));
            } catch (IOException e) {
                // um PDF corrompido não derruba os demais
                results.add(new UploadResult(fileName, 0, "erro: " + e.getMessage()));
            }
        }
        return results;
    }

    @DeleteMapping("/institutional-documents/{fileName}")
    public ResponseEntity<Void> delete(@PathVariable String fileName) {
        ingestionService.deleteByFileName(fileName);
        return ResponseEntity.noContent().build();
    }
}