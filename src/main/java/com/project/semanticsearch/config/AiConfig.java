package com.project.semanticsearch.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    CommandLineRunner warmupModel(EmbeddingModel model) {
        return args -> {
            System.out.println(">>> AI: Starting model warmup and downloading files if necessary...");
            model.embed("warmup");
            System.out.println(">>> AI: Model is READY! You can now send requests.");
        };
    }
}
