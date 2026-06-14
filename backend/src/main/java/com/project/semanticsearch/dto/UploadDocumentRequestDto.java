package com.project.semanticsearch.dto;

public record UploadDocumentRequestDto(
        String fileName,
        String sourceType,
        String content
) {
}
