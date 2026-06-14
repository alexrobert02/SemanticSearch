package com.project.semanticsearch.dto;

public record ChunkDto(
        Long id,
        Integer chunkIndex,
        Integer pageNumber,
        String content,
        int characterCount
) {
}
