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
import java.util.Map;

@Service
public class DocumentIngestionService {

    private final EmbeddingStoreIngestor ingestor;

    public DocumentIngestionService(EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore) {
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
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String textoPagina = stripper.getText(pdf);

                if (textoPagina.isBlank()) continue; // página vazia ou só imagem: Document.from recusaria

                Metadata metadata = Metadata.from(Map.of(
                        "fileName", fileName,
                        "page", String.valueOf(page)
                ));

                ingestor.ingest(Document.from(textoPagina, metadata));
                paginasIngeridas++;
            }
        }
        return paginasIngeridas;
    }
}
