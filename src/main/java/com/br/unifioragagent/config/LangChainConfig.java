package com.br.unifioragagent.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

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
