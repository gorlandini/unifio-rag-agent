package com.br.unifioragagent.controller;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@RestController
public class ChatController {

    private static final int TOP_K = 4;
    private static final int MAX_PAGINAS_CONTEXTO = 6; // limita o prompt (cada página do PPC tem ~3 mil caracteres)

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public ChatController(ChatModel chatModel, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public record ChatRequest(String question) {
    }

    public record ChatResponse(String answer) {
    }

    private record PageRef(String fileName, int page) {
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String answer = chatModel.chat(request.question());
        return new ChatResponse(answer);
    }


    @PostMapping("/chat2")
    public ChatResponse chat2(@RequestBody ChatRequest request) {
        Embedding questionEmbedding = embeddingModel.embed(request.question()).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(TOP_K)
                        .build()
        ).matches();
        // debug: mostra quais trechos a busca trouxe (score, arquivo, página, início do texto)
        matches.forEach(m -> System.out.printf("%.3f | %s p.%s | %s%n",
                m.score(),
                m.embedded().metadata().getString("fileName"),
                m.embedded().metadata().getString("page"),
                m.embedded().text().substring(0, Math.min(120, m.embedded().text().length())).replace("\n", " ")));

        String contexto = montarContextoComVizinhos(matches, questionEmbedding);
        String prompt = """
                Responda usando apenas o contexto abaixo. Se não estiver lá, diga que não sabe.
                Contexto:
                %s
                Pergunta: %s
                """.formatted(contexto, request.question());
        return new ChatResponse(chatModel.chat(prompt));
    }

    // Expansão de vizinhos: para cada página encontrada, manda ao LLM a página inteira + a anterior e a seguinte.
    private String montarContextoComVizinhos(List<EmbeddingMatch<TextSegment>> matches, Embedding questionEmbedding) {
        List<String> semPagina = new ArrayList<>(); // ex.: fatos de exemplo do LangChainConfig
        List<PageRef> encontradas = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            Metadata md = match.embedded().metadata();
            if (md.getString("fileName") == null || md.getString("page") == null) {
                semPagina.add(match.embedded().text());
            } else {
                encontradas.add(new PageRef(md.getString("fileName"), Integer.parseInt(md.getString("page"))));
            }
        }

        // primeiro as páginas encontradas (em ordem de score), depois as vizinhas, até o limite
        Set<PageRef> paginas = new LinkedHashSet<>();
        encontradas.forEach(p -> addAteLimite(paginas, p));
        for (PageRef p : encontradas) {
            addAteLimite(paginas, new PageRef(p.fileName(), p.page() - 1));
            addAteLimite(paginas, new PageRef(p.fileName(), p.page() + 1));
        }
        System.out.println("Páginas no contexto: " + paginas);

        // busca todos os trechos dessas páginas, filtrando por metadado (arquivo + página)
        Map<String, List<String>> paginasPorArquivo = new LinkedHashMap<>();
        paginas.forEach(p -> paginasPorArquivo.computeIfAbsent(p.fileName(), k -> new ArrayList<>()).add(String.valueOf(p.page())));

        List<TextSegment> trechos = new ArrayList<>();
        paginasPorArquivo.forEach((fileName, pages) -> embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .filter(metadataKey("fileName").isEqualTo(fileName).and(metadataKey("page").isIn(pages)))
                        .maxResults(500)
                        .build()
        ).matches().forEach(m -> trechos.add(m.embedded())));

        // reordena na ordem de leitura: arquivo → página → posição do trecho na página
        trechos.sort(Comparator
                .comparing((TextSegment s) -> s.metadata().getString("fileName"))
                .thenComparingInt(s -> Integer.parseInt(s.metadata().getString("page")))
                .thenComparingInt(s -> Integer.parseInt(String.valueOf(s.metadata().toMap().getOrDefault("index", "0")))));

        Map<String, String> textoPorPagina = trechos.stream().collect(Collectors.groupingBy(
                s -> "[" + s.metadata().getString("fileName") + ", página " + s.metadata().getString("page") + "]",
                LinkedHashMap::new,
                Collectors.mapping(TextSegment::text, Collectors.joining("\n"))));

        List<String> blocos = new ArrayList<>(semPagina);
        textoPorPagina.forEach((cabecalho, texto) -> blocos.add(cabecalho + "\n" + texto));
        return String.join("\n\n", blocos);
    }

    private static void addAteLimite(Set<PageRef> paginas, PageRef p) {
        if (p.page() >= 1 && paginas.size() < MAX_PAGINAS_CONTEXTO) {
            paginas.add(p);
        }
    }
}
