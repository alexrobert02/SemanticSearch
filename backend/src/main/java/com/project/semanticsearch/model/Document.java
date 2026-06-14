package com.project.semanticsearch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "pdf_title")
    private String pdfTitle;

    @Column(name = "pdf_author")
    private String pdfAuthor;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentChunk> chunks = new ArrayList<>();

    public void addChunk(DocumentChunk chunk) {
        chunks.add(chunk);
        chunk.setDocument(this);
    }

    public void removeChunk(DocumentChunk chunk) {
        chunks.remove(chunk);
        chunk.setDocument(null);
    }
}
