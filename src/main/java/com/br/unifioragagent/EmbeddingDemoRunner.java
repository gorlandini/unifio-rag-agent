package com.br.unifioragagent;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
@Component
public class EmbeddingDemoRunner implements CommandLineRunner {
    private final EmbeddingModel embeddingModel;
    public EmbeddingDemoRunner(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    @Override
    public void run(String... args) {
        Embedding pergunta = embeddingModel.embed("Qual a carga horária do estágio?").content();
        Embedding relacionado = embeddingModel.embed("O estágio supervisionado tem 400 horas.").content();
        Embedding naoRelacionado = embeddingModel.embed("O cardápio do restaurante universitário mudou.").content();
        double simRelacionado = CosineSimilarity.between(pergunta, relacionado);
        double simNaoRelacionado = CosineSimilarity.between(pergunta, naoRelacionado);
        System.out.println("Dimensão do vetor: " + pergunta.vector().length);
        System.out.println("Similaridade com frase relacionada: " + simRelacionado);
        System.out.println("Similaridade com frase não relacionada: " + simNaoRelacionado);
    }
}
