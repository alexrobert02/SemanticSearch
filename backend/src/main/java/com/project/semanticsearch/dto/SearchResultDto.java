package com.project.semanticsearch.dto;

public record SearchResultDto(
        Long chunkId,
        Long documentId,
        String fileName,
        String sourceType,
        String pdfTitle,
        String pdfAuthor,
        String content,
        String highlightedContent,
        Integer chunkIndex,
        Integer pageNumber,
        Double semanticScore,
        Double keywordScore,
        Double combinedScore,
        String rankExplanation,
        String matchType
) {
}
