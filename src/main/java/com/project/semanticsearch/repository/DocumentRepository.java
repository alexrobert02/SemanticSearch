package com.project.semanticsearch.repository;

import com.project.semanticsearch.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Query(value = """
        SELECT * FROM documente 
        ORDER BY VECTOR_DISTANCE(embedding, TO_VECTOR(:searchVector), COSINE) 
        FETCH FIRST :resultLimit ROWS ONLY
        """, nativeQuery = true)
    List<Document> findSimilarDocuments(
            @Param("searchVector") String searchVector,
            @Param("resultLimit") int resultLimit
    );
}
