package com.project.semanticsearch.service;

import com.project.semanticsearch.dto.ChunkDto;
import com.project.semanticsearch.dto.DocumentSearchResultDto;
import com.project.semanticsearch.dto.DocumentStatsDto;
import com.project.semanticsearch.dto.DocumentSummaryDto;
import com.project.semanticsearch.dto.SearchResultDto;
import com.project.semanticsearch.model.Document;
import com.project.semanticsearch.model.DocumentChunk;
import com.project.semanticsearch.repository.DocumentChunkRepository;
import com.project.semanticsearch.repository.DocumentRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.BreakIterator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 160;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 25;
    private static final int MAX_HIGHLIGHTED_SENTENCES = 2;
    private static final double MIN_SEMANTIC_HIGHLIGHT_SCORE = 0.15;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingModel embeddingModel;
    private final TextChunker textChunker;
    private final DocumentTextExtractorService textExtractorService;

    @Transactional
    public DocumentSummaryDto saveDocumentWithChunks(String fileName, String sourceType, String content) {
        String safeFileName = normalizeFileName(fileName);
        ExtractedDocument extractedDocument = new ExtractedDocument(
                safeFileName,
                normalizeSourceType(sourceType, safeFileName),
                null,
                null,
                null,
                List.of(new ExtractedPage(null, content))
        );

        return saveExtractedDocument(extractedDocument);
    }

    @Transactional
    public DocumentSummaryDto saveMultipartDocument(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        return saveExtractedDocument(textExtractorService.extractDocument(file));
    }

    @Transactional(readOnly = true)
    public List<SearchResultDto> search(String query, int limit, String mode, Long documentId, boolean rerank) {
        String normalizedMode = mode == null ? "semantic" : mode.toLowerCase(Locale.ROOT).trim();
        int safeLimit = normalizeLimit(limit);

        return switch (normalizedMode) {
            case "keyword" -> searchKeyword(query, safeLimit, documentId, rerank);
            case "hybrid" -> searchHybrid(query, safeLimit, documentId, rerank);
            default -> searchSemantically(query, safeLimit, documentId, rerank);
        };
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResultDto> searchAggregatedByDocument(
            String query,
            int limit,
            String mode,
            Long documentId,
            boolean rerank
    ) {
        int safeLimit = normalizeLimit(limit);
        List<SearchResultDto> chunkResults = search(query, Math.min(MAX_LIMIT, safeLimit * 4), mode, documentId, rerank);

        return chunkResults.stream()
                .collect(Collectors.groupingBy(SearchResultDto::documentId, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(results -> {
                    SearchResultDto best = results.stream()
                            .max(Comparator.comparing(SearchResultDto::combinedScore))
                            .orElseThrow();
                    double averageScore = results.stream()
                            .mapToDouble(SearchResultDto::combinedScore)
                            .average()
                            .orElse(0.0);

                    return new DocumentSearchResultDto(
                            best.documentId(),
                            best.fileName(),
                            best.sourceType(),
                            best.pdfTitle(),
                            best.pdfAuthor(),
                            best.pageNumber(),
                            best.combinedScore(),
                            averageScore,
                            results.size(),
                            results.stream()
                                    .sorted(Comparator.comparing(SearchResultDto::combinedScore).reversed())
                                    .limit(3)
                                    .toList()
                    );
                })
                .sorted(Comparator.comparing(DocumentSearchResultDto::bestScore).reversed())
                .limit(safeLimit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SearchResultDto> searchSemantically(String query, int limit, Long documentId, boolean rerank) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int candidateLimit = Math.min(MAX_LIMIT, Math.max(limit * 3, limit));
        String queryEmbedding = toOracleVector(embeddingModel.embed(query).content().vector());

        List<SearchCandidate> candidates = documentChunkRepository.semanticSearch(queryEmbedding, candidateLimit, documentId)
                .stream()
                .map(row -> mapSemanticRowToCandidate(row, query))
                .toList();

        return rankCandidates(candidates, query, "semantic", rerank).stream()
                .limit(normalizeLimit(limit))
                .map(candidate -> candidate.toDto("semantic"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SearchResultDto> searchKeyword(String query, int limit, Long documentId, boolean rerank) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<SearchCandidate> candidates = documentChunkRepository.keywordSearch(query, documentId).stream()
                .map(chunk -> mapKeywordChunkToCandidate(chunk, query))
                .toList();

        return rankCandidates(candidates, query, "keyword", rerank).stream()
                .limit(normalizeLimit(limit))
                .map(candidate -> candidate.toDto("keyword"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDto> getAllDocuments() {
        return documentRepository.findAll().stream()
                .sorted(Comparator.comparing(Document::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::mapDocumentToSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChunkDto> getChunksForDocument(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new EntityNotFoundException("Document not found with id " + documentId);
        }

        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(chunk -> new ChunkDto(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getPageNumber(),
                        chunk.getContent(),
                        chunk.getContent() == null ? 0 : chunk.getContent().length()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentStatsDto getStats() {
        List<Document> documents = documentRepository.findAll();
        List<DocumentChunk> chunks = documentChunkRepository.findAll();

        long documentCount = documents.size();
        long chunkCount = chunks.size();
        long totalCharacters = chunks.stream()
                .map(DocumentChunk::getContent)
                .filter(content -> content != null)
                .mapToLong(String::length)
                .sum();

        LocalDateTime lastUploadedAt = documents.stream()
                .map(Document::getUploadedAt)
                .filter(date -> date != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Long> sourceTypeCounts = documents.stream()
                .map(document -> document.getSourceType() == null ? "manual" : document.getSourceType())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        return new DocumentStatsDto(
                documentCount,
                chunkCount,
                totalCharacters,
                documentCount == 0 ? 0.0 : (double) chunkCount / documentCount,
                lastUploadedAt,
                sourceTypeCounts
        );
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new EntityNotFoundException("Document not found with id " + documentId);
        }

        documentRepository.deleteById(documentId);
    }

    private DocumentSummaryDto saveExtractedDocument(ExtractedDocument extractedDocument) {
        String safeFileName = normalizeFileName(extractedDocument.fileName());
        String safeSourceType = normalizeSourceType(extractedDocument.sourceType(), safeFileName);

        List<TextChunker.TextChunk> chunks = textChunker.chunkPages(extractedDocument.pages(), CHUNK_SIZE, CHUNK_OVERLAP);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document did not produce any searchable chunks");
        }

        Document document = Document.builder()
                .fileName(safeFileName)
                .sourceType(safeSourceType)
                .uploadedAt(LocalDateTime.now())
                .pdfTitle(extractedDocument.pdfTitle())
                .pdfAuthor(extractedDocument.pdfAuthor())
                .fileSizeBytes(extractedDocument.fileSizeBytes())
                .build();

        int index = 0;
        for (TextChunker.TextChunk textChunk : chunks) {
            String normalizedChunk = normalizeContent(textChunk.content());
            if (normalizedChunk.isBlank()) {
                continue;
            }

            DocumentChunk chunk = DocumentChunk.builder()
                    .chunkIndex(index++)
                    .pageNumber(textChunk.pageNumber())
                    .content(normalizedChunk)
                    .embedding(toOracleVector(embeddingModel.embed(normalizedChunk).content().vector()))
                    .build();

            document.addChunk(chunk);
        }

        if (document.getChunks().isEmpty()) {
            throw new IllegalArgumentException("Document did not produce any searchable chunks");
        }

        return mapDocumentToSummary(documentRepository.save(document));
    }

    private List<SearchResultDto> searchHybrid(String query, int limit, Long documentId, boolean rerank) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int candidateLimit = Math.min(MAX_LIMIT, Math.max(limit * 3, limit));
        Map<Long, SearchCandidate> merged = new LinkedHashMap<>();

        String queryEmbedding = toOracleVector(embeddingModel.embed(query).content().vector());
        documentChunkRepository.semanticSearch(queryEmbedding, candidateLimit, documentId).stream()
                .map(row -> mapSemanticRowToCandidate(row, query))
                .forEach(candidate -> merged.merge(candidate.chunkId, candidate, SearchCandidate::merge));

        documentChunkRepository.keywordSearch(query, documentId).stream()
                .map(chunk -> mapKeywordChunkToCandidate(chunk, query))
                .forEach(candidate -> merged.merge(candidate.chunkId, candidate, SearchCandidate::merge));

        return rankCandidates(new ArrayList<>(merged.values()), query, "hybrid", rerank).stream()
                .limit(limit)
                .map(candidate -> candidate.toDto(candidate.semanticScore > 0.0 && candidate.keywordScore > 0.0
                        ? "hybrid"
                        : candidate.semanticScore > 0.0 ? "semantic" : "keyword"))
                .toList();
    }

    private List<SearchCandidate> rankCandidates(List<SearchCandidate> candidates, String query, String mode, boolean rerank) {
        Map<Long, Long> matchesPerDocument = candidates.stream()
                .collect(Collectors.groupingBy(candidate -> candidate.documentId, Collectors.counting()));

        candidates.forEach(candidate -> {
            candidate.exactPhraseScore = scoreExactPhrase(query, candidate.content);
            candidate.metadataScore = scoreMetadataMatch(query, candidate.fileName, candidate.pdfTitle, candidate.pdfAuthor);
            candidate.recencyScore = scoreRecency(candidate.uploadedAt);
            candidate.lengthScore = scoreChunkLength(candidate.content);
            candidate.documentBoost = Math.min(0.08, matchesPerDocument.getOrDefault(candidate.documentId, 1L) * 0.02);
            candidate.combinedScore = rerank ? scoreCandidate(candidate, mode) : scoreCandidateWithoutRerank(candidate, mode);
            candidate.rankExplanation = rerank
                    ? buildRankExplanation(candidate)
                    : buildNoRerankExplanation(candidate, mode);
            candidate.highlightedContent = highlightSemantically(candidate.content, query);
        });

        return candidates.stream()
                .sorted(Comparator.comparing(SearchCandidate::combinedScore).reversed())
                .toList();
    }

    private double scoreCandidate(SearchCandidate candidate, String mode) {
        double score = switch (mode) {
            case "keyword" -> candidate.keywordScore * 0.70
                    + candidate.exactPhraseScore * 0.15
                    + candidate.metadataScore * 0.05
                    + candidate.recencyScore * 0.03
                    + candidate.lengthScore * 0.07;
            case "hybrid" -> candidate.semanticScore * 0.55
                    + candidate.keywordScore * 0.25
                    + candidate.exactPhraseScore * 0.08
                    + candidate.metadataScore * 0.05
                    + candidate.recencyScore * 0.03
                    + candidate.lengthScore * 0.04;
            default -> candidate.semanticScore * 0.72
                    + candidate.keywordScore * 0.08
                    + candidate.exactPhraseScore * 0.08
                    + candidate.metadataScore * 0.05
                    + candidate.recencyScore * 0.03
                    + candidate.lengthScore * 0.04;
        };

        return clampScore(score + candidate.documentBoost);
    }

    private double scoreCandidateWithoutRerank(SearchCandidate candidate, String mode) {
        return switch (mode) {
            case "keyword" -> clampScore(candidate.keywordScore);
            case "hybrid" -> clampScore((candidate.semanticScore * 0.75) + (candidate.keywordScore * 0.25));
            default -> clampScore(candidate.semanticScore);
        };
    }

    private SearchCandidate mapSemanticRowToCandidate(Object[] row, String query) {
        SearchCandidate candidate = new SearchCandidate();
        candidate.chunkId = ((Number) row[0]).longValue();
        candidate.documentId = ((Number) row[1]).longValue();
        candidate.fileName = stringValue(row[2]);
        candidate.sourceType = stringValue(row[3]);
        candidate.pdfTitle = stringValue(row[4]);
        candidate.pdfAuthor = stringValue(row[5]);
        candidate.uploadedAt = toLocalDateTime(row[6]);
        candidate.content = stringValue(row[7]);
        candidate.chunkIndex = ((Number) row[8]).intValue();
        candidate.pageNumber = row[9] == null ? null : ((Number) row[9]).intValue();
        double distance = ((Number) row[10]).doubleValue();
        candidate.semanticScore = clampScore(1.0 - distance);
        candidate.keywordScore = scoreKeywordMatch(query, candidate.content);
        return candidate;
    }

    private SearchCandidate mapKeywordChunkToCandidate(DocumentChunk chunk, String query) {
        SearchCandidate candidate = new SearchCandidate();
        candidate.chunkId = chunk.getId();
        candidate.documentId = chunk.getDocument().getId();
        candidate.fileName = chunk.getDocument().getFileName();
        candidate.sourceType = chunk.getDocument().getSourceType();
        candidate.pdfTitle = chunk.getDocument().getPdfTitle();
        candidate.pdfAuthor = chunk.getDocument().getPdfAuthor();
        candidate.uploadedAt = chunk.getDocument().getUploadedAt();
        candidate.content = chunk.getContent();
        candidate.chunkIndex = chunk.getChunkIndex();
        candidate.pageNumber = chunk.getPageNumber();
        candidate.semanticScore = 0.0;
        candidate.keywordScore = scoreKeywordMatch(query, candidate.content);
        return candidate;
    }

    private DocumentSummaryDto mapDocumentToSummary(Document document) {
        return new DocumentSummaryDto(
                document.getId(),
                document.getFileName(),
                document.getSourceType(),
                document.getUploadedAt(),
                document.getPdfTitle(),
                document.getPdfAuthor(),
                document.getFileSizeBytes(),
                documentChunkRepository.countByDocumentId(document.getId())
        );
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\u0000", " ").replaceAll("[\\t\\x0B\\f\\r]+", " ").trim();
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "untitled.txt";
        }
        return fileName.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String normalizeSourceType(String sourceType, String fileName) {
        String normalized = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        if (List.of("pdf", "txt", "md", "manual").contains(normalized)) {
            return normalized;
        }
        return textExtractorService.detectSourceType(fileName);
    }

    private int normalizeLimit(int limit) {
        return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
    }

    private String toOracleVector(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private double scoreKeywordMatch(String query, String content) {
        List<String> terms = extractTerms(query);
        if (terms.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);
        long matchedTerms = terms.stream().filter(lowerContent::contains).count();
        return clampScore((double) matchedTerms / terms.size());
    }

    private double scoreExactPhrase(String query, String content) {
        if (query == null || query.isBlank() || content == null || content.isBlank()) {
            return 0.0;
        }
        return content.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim()) ? 1.0 : 0.0;
    }

    private double scoreMetadataMatch(String query, String... metadataValues) {
        List<String> terms = extractTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }

        String metadata = java.util.Arrays.stream(metadataValues)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        if (metadata.isBlank()) {
            return 0.0;
        }

        long matchedTerms = terms.stream().filter(metadata::contains).count();
        return clampScore((double) matchedTerms / terms.size());
    }

    private double scoreRecency(LocalDateTime uploadedAt) {
        if (uploadedAt == null) {
            return 0.0;
        }

        long days = Math.max(0, Duration.between(uploadedAt, LocalDateTime.now()).toDays());
        return clampScore(1.0 / (1.0 + (days / 30.0)));
    }

    private double scoreChunkLength(String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }

        int targetLength = 850;
        int length = content.length();
        return clampScore(1.0 - (Math.abs(length - targetLength) / (double) targetLength));
    }

    private String buildRankExplanation(SearchCandidate candidate) {
        return "semantic=" + percent(candidate.semanticScore)
                + ", keyword=" + percent(candidate.keywordScore)
                + ", exact=" + percent(candidate.exactPhraseScore)
                + ", metadata=" + percent(candidate.metadataScore)
                + ", recency=" + percent(candidate.recencyScore)
                + ", length=" + percent(candidate.lengthScore)
                + ", documentBoost=" + percent(candidate.documentBoost);
    }

    private String buildNoRerankExplanation(SearchCandidate candidate, String mode) {
        return switch (mode) {
            case "keyword" -> "re-ranking dezactivat: ordonare dupa scor keyword="
                    + percent(candidate.keywordScore);
            case "hybrid" -> "re-ranking dezactivat: scor hibrid simplu semantic="
                    + percent(candidate.semanticScore)
                    + ", keyword=" + percent(candidate.keywordScore);
            default -> "re-ranking dezactivat: ordonare dupa similaritate vectoriala="
                    + percent(candidate.semanticScore);
        };
    }

    private String highlightSemantically(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }

        List<TextSegment> segments = splitIntoSentenceSegments(content);
        if (segments.isEmpty() || query == null || query.isBlank()) {
            return escapeHtml(content);
        }

        float[] queryVector = embeddingModel.embed(query).content().vector();
        List<ScoredSegment> scoredSegments = segments.stream()
                .filter(segment -> segment.text() != null && !segment.text().isBlank())
                .map(segment -> new ScoredSegment(segment, cosineSimilarity(
                        queryVector,
                        embeddingModel.embed(segment.text()).content().vector()
                )))
                .sorted(Comparator.comparing(ScoredSegment::score).reversed())
                .toList();

        if (scoredSegments.isEmpty() || scoredSegments.get(0).score() < MIN_SEMANTIC_HIGHLIGHT_SCORE) {
            return escapeHtml(content);
        }

        Set<Integer> highlightedStarts = scoredSegments.stream()
                .limit(MAX_HIGHLIGHTED_SENTENCES)
                .map(scoredSegment -> scoredSegment.segment().start())
                .collect(Collectors.toCollection(HashSet::new));

        StringBuilder html = new StringBuilder();
        for (TextSegment segment : segments) {
            String escaped = escapeHtml(segment.text());
            if (highlightedStarts.contains(segment.start())) {
                html.append("<mark class=\"semantic-highlight\">").append(escaped).append("</mark>");
            } else {
                html.append(escaped);
            }
        }

        return html.toString();
    }

    private List<TextSegment> splitIntoSentenceSegments(String content) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag("ro-RO"));
        iterator.setText(content);

        List<TextSegment> segments = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = content.substring(start, end);
            if (!sentence.isBlank()) {
                segments.add(new TextSegment(start, sentence));
            }
        }

        if (segments.isEmpty()) {
            segments.add(new TextSegment(0, content));
        }

        return segments;
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        int length = Math.min(left.length, right.length);

        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<String> extractTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream()
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }

    private record TextSegment(int start, String text) {
    }

    private record ScoredSegment(TextSegment segment, double score) {
    }

    private static final class SearchCandidate {
        private Long chunkId;
        private Long documentId;
        private String fileName;
        private String sourceType;
        private String pdfTitle;
        private String pdfAuthor;
        private LocalDateTime uploadedAt;
        private String content;
        private String highlightedContent;
        private Integer chunkIndex;
        private Integer pageNumber;
        private double semanticScore;
        private double keywordScore;
        private double exactPhraseScore;
        private double metadataScore;
        private double recencyScore;
        private double lengthScore;
        private double documentBoost;
        private double combinedScore;
        private String rankExplanation;

        private static SearchCandidate merge(SearchCandidate left, SearchCandidate right) {
            left.semanticScore = Math.max(left.semanticScore, right.semanticScore);
            left.keywordScore = Math.max(left.keywordScore, right.keywordScore);
            return left;
        }

        private double combinedScore() {
            return combinedScore;
        }

        private SearchResultDto toDto(String matchType) {
            return new SearchResultDto(
                    chunkId,
                    documentId,
                    fileName,
                    sourceType,
                    pdfTitle,
                    pdfAuthor,
                    content,
                    highlightedContent,
                    chunkIndex,
                    pageNumber,
                    semanticScore,
                    keywordScore,
                    combinedScore,
                    rankExplanation,
                    matchType
            );
        }
    }
}
