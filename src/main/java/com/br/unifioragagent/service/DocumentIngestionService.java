package com.br.unifioragagent.service;



import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class DocumentIngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingStoreIngestor ingestor;

    public DocumentIngestionService(EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
        // o ingestor faz: quebrar em pedaços → gerar vetor de cada pedaço → guardar no store
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50)) // até 500 chars, 50 de sobreposição
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    public int ingestPdf(byte[] pdfBytes, String fileName) throws IOException {
        int paginasIngeridas = 0;

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            // só apaga a versão anterior depois que o PDF novo abriu: um arquivo corrompido não some com o antigo
            deleteByFileName(fileName);
            String uploadedAt = Instant.now().toString();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String textoPagina = stripper.getText(pdf);

                if (textoPagina.isBlank()) continue; // página vazia ou só imagem: Document.from recusaria

                Metadata metadata = Metadata.from(Map.of(
                        "fileName", fileName,
                        "page", String.valueOf(page),
                        "uploaded_at", uploadedAt
                ));

                ingestor.ingest(Document.from(textoPagina, metadata));
                paginasIngeridas++;
            }
        }
        return paginasIngeridas;
    }

    // reenvio substitui: apaga todos os trechos daquele arquivo
    public void deleteByFileName(String fileName) {
        embeddingStore.removeAll(metadataKey("fileName").isEqualTo(fileName));
    }
}
