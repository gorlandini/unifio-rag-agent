package com.br.unifioragagent.controller;

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

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ChatController {

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
                        .maxResults(2)
                        .build()
        ).matches();
        String contexto = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n"));
        String prompt = """
                Responda usando apenas o contexto abaixo. Se não estiver lá, diga que não sabe.
                Contexto:
                %s
                Pergunta: %s
                """.formatted(contexto, request.question());
        return new ChatResponse(chatModel.chat(prompt));


    }
}