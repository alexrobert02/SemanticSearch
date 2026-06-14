package com.project.semanticsearch.service;

import java.util.List;

public record ExtractedDocument(
        String fileName,
        String sourceType,
        String pdfTitle,
        String pdfAuthor,
        Long fileSizeBytes,
        List<ExtractedPage> pages
) {
}
