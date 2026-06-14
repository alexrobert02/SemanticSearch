package com.semanticsearch.backend.dto;

public record UploadDocumentRequestDto(
        String fileName,
        String sourceType,
        String content
) {
}
