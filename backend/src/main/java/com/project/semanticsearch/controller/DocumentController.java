package com.project.semanticsearch.controller;

import com.project.semanticsearch.entity.Document;
import com.project.semanticsearch.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public Document uploadDocument(@RequestParam String fileName, @RequestBody String content) {
        return documentService.saveDocument(fileName, content);
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam String query, @RequestParam(defaultValue = "5") int limit) {
        return documentService.searchSemantically(query, limit);
    }
}
