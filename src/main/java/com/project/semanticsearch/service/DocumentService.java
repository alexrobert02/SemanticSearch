package com.project.semanticsearch.service;

import com.project.semanticsearch.entity.Document;
import com.project.semanticsearch.repository.DocumentRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;

    public Document saveDocument(String fileName, String content) {
        float[] vectorArray = embeddingModel.embed(content).content().vector();

        // Transformam float[] in formatul de text "[0.1, 0.2, ...]"
        String vectorString = Arrays.toString(vectorArray);

        Document doc = new Document();
        doc.setFileName(fileName);
        doc.setContent(content);
        doc.setEmbedding(vectorString);

        return documentRepository.save(doc);
    }

    public List<Document> searchSemantically(String query, int limit) {
        float[] queryVector = embeddingModel.embed(query).content().vector();
        String queryVectorString = Arrays.toString(queryVector);

        return documentRepository.findSimilarDocuments(queryVectorString, limit);
    }
}
