package com.project.semanticsearch.controller;

import com.project.semanticsearch.dto.ChunkDto;
import com.project.semanticsearch.dto.DocumentSearchResultDto;
import com.project.semanticsearch.dto.DocumentStatsDto;
import com.project.semanticsearch.dto.DocumentSummaryDto;
import com.project.semanticsearch.dto.SearchResultDto;
import com.project.semanticsearch.dto.UploadDocumentRequestDto;
import com.project.semanticsearch.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<DocumentSummaryDto> uploadDocument(
            @RequestParam String fileName,
            @RequestBody String content
    ) {
        return ResponseEntity.ok(documentService.saveDocumentWithChunks(fileName, "manual", content));
    }

    @PostMapping(value = "/upload-json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentSummaryDto> uploadDocumentJson(@RequestBody UploadDocumentRequestDto request) {
        return ResponseEntity.ok(documentService.saveDocumentWithChunks(
                request.fileName(),
                request.sourceType(),
                request.content()
        ));
    }

    @PostMapping("/upload-file")
    public ResponseEntity<DocumentSummaryDto> uploadMultipartFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(documentService.saveMultipartDocument(file));
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "semantic") String mode,
            @RequestParam(required = false) Long documentId,
            @RequestParam(defaultValue = "true") boolean rerank
    ) {
        return documentService.search(query, limit, mode, documentId, rerank);
    }

    @GetMapping("/search/documents")
    public List<DocumentSearchResultDto> searchByDocument(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "hybrid") String mode,
            @RequestParam(required = false) Long documentId,
            @RequestParam(defaultValue = "true") boolean rerank
    ) {
        return documentService.searchAggregatedByDocument(query, limit, mode, documentId, rerank);
    }

    @GetMapping
    public List<DocumentSummaryDto> listDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/{documentId}/chunks")
    public List<ChunkDto> getChunks(@PathVariable Long documentId) {
        return documentService.getChunksForDocument(documentId);
    }

    @GetMapping("/stats")
    public DocumentStatsDto getStats() {
        return documentService.getStats();
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
