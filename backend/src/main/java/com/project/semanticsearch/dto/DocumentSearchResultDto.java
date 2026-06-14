package com.project.semanticsearch.dto;

import java.util.List;

public record DocumentSearchResultDto(
        Long documentId,
        String fileName,
        String sourceType,
        String pdfTitle,
        String pdfAuthor,
        Integer bestPageNumber,
        Double bestScore,
        Double averageScore,
        int matchedChunkCount,
        List<SearchResultDto> topChunks
) {
}
