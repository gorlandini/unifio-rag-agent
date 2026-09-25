package com.br.unifioragagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class LangChainConfig {
    @Bean
    public ChatModel chatLanguageModel(@Value("${unifio.chat.model}") String modelName) {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName(modelName)
                .temperature(0.3)
                .numCtx(8192) // janela de contexto; o padrão do Ollama corta prompts longos sem avisar
                .build();
    }


    @Bean
    public EmbeddingModel embeddingModel(@Value("${unifio.embedding.model}") String modelName) {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName(modelName)
                .build();
    }



    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(@Value("${unifio.embedding.dimension}") int dimension) {
        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("ragdb")
                .user("rag")
                .password("rag")
                .table("institutional_embeddings")
                .dimension(dimension) // precisa bater com o modelo: nomic-embed-text = 768, bge-m3 = 1024
                .createTable(true)    // cria a extensão vector e a tabela se não existirem
                .build();
    }
}
