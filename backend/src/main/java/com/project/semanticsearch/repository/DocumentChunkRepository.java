package com.project.semanticsearch.repository;

import com.project.semanticsearch.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Query(value = """
        SELECT
            dc.id AS chunkId,
            d.id AS documentId,
            d.file_name AS fileName,
            d.source_type AS sourceType,
            d.pdf_title AS pdfTitle,
            d.pdf_author AS pdfAuthor,
            d.uploaded_at AS uploadedAt,
            DBMS_LOB.SUBSTR(dc.content, 4000, 1) AS content,
            dc.chunk_index AS chunkIndex,
            dc.page_number AS pageNumber,
            VECTOR_DISTANCE(dc.embedding, TO_VECTOR(:searchVector), COSINE) AS distance
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE dc.embedding IS NOT NULL
          AND (:documentId IS NULL OR d.id = :documentId)
        ORDER BY VECTOR_DISTANCE(dc.embedding, TO_VECTOR(:searchVector), COSINE)
        FETCH FIRST :resultLimit ROWS ONLY
        """, nativeQuery = true)
    List<Object[]> semanticSearch(
            @Param("searchVector") String searchVector,
            @Param("resultLimit") int resultLimit,
            @Param("documentId") Long documentId
    );

    @Query(value = """
        SELECT dc.*
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE DBMS_LOB.INSTR(LOWER(dc.content), LOWER(:query)) > 0
          AND (:documentId IS NULL OR d.id = :documentId)
        ORDER BY dc.chunk_index ASC
        """, nativeQuery = true)
    List<DocumentChunk> keywordSearch(@Param("query") String query, @Param("documentId") Long documentId);

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    long countByDocumentId(Long documentId);
}
