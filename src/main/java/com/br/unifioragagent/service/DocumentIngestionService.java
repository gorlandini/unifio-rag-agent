package com.br.unifioragagent.service;



import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentSplitter splitter = DocumentSplitters.recursive(500, 50); // até 500 chars, 50 de sobreposição

    public DocumentIngestionService(EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int ingestPdf(byte[] pdfBytes, String fileName, String description) throws IOException {
        int paginasIngeridas = 0;

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            // só apaga a versão anterior depois que o PDF novo abriu: um arquivo corrompido não some com o antigo
            deleteByFileName(fileName);
            String uploadedAt = Instant.now().toString();
            String titulo = tituloDoArquivo(fileName);
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String textoPagina = stripper.getText(pdf);

                if (textoPagina.isBlank()) continue; // página vazia ou só imagem: Document.from recusaria

                // a descrição se repete em todos os trechos do arquivo: é o que deixa o GET montar a lista só com a tabela
                Metadata metadata = Metadata.from(Map.of(
                        "fileName", fileName,
                        "page", String.valueOf(page),
                        "uploaded_at", uploadedAt,
                        "description", description
                ));

                List<TextSegment> trechos = splitter.split(Document.from(textoPagina, metadata));
                // o vetor é gerado com um cabeçalho de contexto ("[PPC Engenharia De Software, página 3]"), mas o
                // store guarda o trecho original: o /chat2 já monta o próprio cabeçalho por página no prompt
                String cabecalho = "[" + titulo + ", página " + page + "]\n";
                List<TextSegment> comCabecalho = trechos.stream()
                        .map(t -> TextSegment.from(cabecalho + t.text(), t.metadata()))
                        .toList();
                List<Embedding> embeddings = embeddingModel.embedAll(comCabecalho).content();
                embeddingStore.addAll(embeddings, trechos);
                paginasIngeridas++;
            }
        }
        return paginasIngeridas;
    }

    // "PPC_EngenhariaDeSoftware.pdf" → "PPC Engenharia De Software" (o trecho da p.3 tem o nome do coordenador, mas não diz o curso)
    static String tituloDoArquivo(String fileName) {
        String base = fileName.replaceFirst("\\.[^.]+$", "").replace('_', ' ');
        return base.replaceAll("(?<=[a-zà-ú])(?=[A-ZÀ-Ú])", " ");
    }

    public record DocumentSummary(String fileName, String description, int pages, int chunks, String uploadedAt) {}

    // documentos que estão no banco, mais recentes primeiro; pages = páginas com texto (as em branco não são indexadas)
    public List<DocumentSummary> listDocuments() {
        return jdbcTemplate.query("""
                SELECT metadata->>'fileName'                AS file_name,
                       max(metadata->>'description')        AS description,
                       count(DISTINCT metadata->>'page')    AS pages,
                       count(*)                             AS chunks,
                       max(metadata->>'uploaded_at')        AS uploaded_at
                FROM institutional_embeddings
                GROUP BY metadata->>'fileName'
                ORDER BY uploaded_at DESC NULLS LAST, file_name
                """,
                (rs, i) -> new DocumentSummary(rs.getString("file_name"), rs.getString("description"), rs.getInt("pages"),
                        rs.getInt("chunks"), rs.getString("uploaded_at")));
    }

    // reenvio substitui: apaga todos os trechos daquele arquivo
    public void deleteByFileName(String fileName) {
        embeddingStore.removeAll(metadataKey("fileName").isEqualTo(fileName));
    }
}
