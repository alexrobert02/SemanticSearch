package com.project.semanticsearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documente") // Maps to the existing Romanian table
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nume_document")
    private String fileName;

    @Lob
    @Column(name = "text_document")
    private String content;

    @Column(name = "embedding", columnDefinition = "VECTOR")
    private String embedding;
}
