package com.project.semanticsearch.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentTextExtractorService {

    public ExtractedDocument extractDocument(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "untitled" : file.getOriginalFilename();
        String sourceType = detectSourceType(fileName);

        if ("pdf".equals(sourceType)) {
            return extractPdf(file, fileName);
        }

        if (!"txt".equals(sourceType) && !"md".equals(sourceType)) {
            throw new IllegalArgumentException("Only PDF, TXT and MD files are supported");
        }

        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return new ExtractedDocument(
                fileName,
                sourceType,
                null,
                null,
                file.getSize(),
                List.of(new ExtractedPage(null, text))
        );
    }

    public String extractText(MultipartFile file) throws IOException {
        return extractDocument(file).pages().stream()
                .map(ExtractedPage::text)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    public String detectSourceType(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return detectSourceType(fileName);
    }

    public String detectSourceType(String fileName) {
        fileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".pdf")) return "pdf";
        if (fileName.endsWith(".txt")) return "txt";
        if (fileName.endsWith(".md")) return "md";
        return "manual";
    }

    private ExtractedDocument extractPdf(MultipartFile file, String fileName) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDDocumentInformation information = document.getDocumentInformation();
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedPage> pages = new ArrayList<>();

            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    pages.add(new ExtractedPage(page, pageText));
                }
            }

            if (pages.isEmpty()) {
                throw new IllegalArgumentException("PDF-ul nu contine text extractibil. Pentru PDF-uri scanate este necesar OCR.");
            }

            return new ExtractedDocument(
                    fileName,
                    "pdf",
                    blankToNull(information.getTitle()),
                    blankToNull(information.getAuthor()),
                    file.getSize(),
                    pages
            );
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
