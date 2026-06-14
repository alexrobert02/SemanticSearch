package com.project.semanticsearch.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record DocumentStatsDto(
        Long documentCount,
        Long chunkCount,
        Long totalCharacters,
        Double averageChunksPerDocument,
        LocalDateTime lastUploadedAt,
        Map<String, Long> sourceTypeCounts
) {
}
