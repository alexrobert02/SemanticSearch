export interface DocumentSummary {
    id: number;
    fileName: string;
    sourceType: string;
    uploadedAt: string;
    pdfTitle: string | null;
    pdfAuthor: string | null;
    fileSizeBytes: number | null;
    chunkCount: number;
}

export interface SearchResult {
    chunkId: number;
    documentId: number;
    fileName: string;
    sourceType: string;
    pdfTitle: string | null;
    pdfAuthor: string | null;
    content: string;
    highlightedContent: string;
    chunkIndex: number;
    pageNumber: number | null;
    semanticScore: number;
    keywordScore: number;
    combinedScore: number;
    rankExplanation: string;
    matchType: 'semantic' | 'keyword' | 'hybrid';
}

export interface Chunk {
    id: number;
    chunkIndex: number;
    pageNumber: number | null;
    content: string;
    characterCount: number;
}

export interface DocumentSearchResult {
    documentId: number;
    fileName: string;
    sourceType: string;
    pdfTitle: string | null;
    pdfAuthor: string | null;
    bestPageNumber: number | null;
    bestScore: number;
    averageScore: number;
    matchedChunkCount: number;
    topChunks: SearchResult[];
}

export interface DocumentStats {
    documentCount: number;
    chunkCount: number;
    totalCharacters: number;
    averageChunksPerDocument: number;
    lastUploadedAt: string | null;
    sourceTypeCounts: Record<string, number>;
}
