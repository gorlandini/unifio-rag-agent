package com.br.unifioragagent.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

@Configuration
public class LangChainConfig {
    @Bean
    public ChatModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.1")
                .temperature(0.3)
                .build();
    }


    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("nomic-embed-text")
                .build();
    }



    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
// documentos de mentira, só para provar o mecanismo antes de ler PDF de verdade
        String[] fatos = {
                "O estágio supervisionado do curso de Engenharia de Software tem carga horária mínima de 400 horas.",
                "As reuniões do colegiado do curso acontecem na primeira segunda-feira de cada mês.",
                "O Trabalho de Conclusão de Curso (TCC) deve ser defendido perante banca de três professores."
        };
        for (String fato : fatos) {
            var embedding = embeddingModel.embed(fato).content();
            store.add(embedding, TextSegment.from(fato, Metadata.from("fonte", "documento-exemplo")));
        }
        return store;
    }


}
