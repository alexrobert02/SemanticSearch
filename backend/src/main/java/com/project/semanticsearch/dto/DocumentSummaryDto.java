package com.project.semanticsearch.dto;

import java.time.LocalDateTime;

public record DocumentSummaryDto(
        Long id,
        String fileName,
        String sourceType,
        LocalDateTime uploadedAt,
        String pdfTitle,
        String pdfAuthor,
        Long fileSizeBytes,
        long chunkCount
) {
}
