package com.project.semanticsearch.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private static final int START_BOUNDARY_SEARCH_WINDOW = 90;

    public record TextChunk(String content, Integer pageNumber) {
    }

    public List<String> chunkText(String text, int chunkSize, int overlap) {
        return chunkTextWithPage(text, null, chunkSize, overlap).stream()
                .map(TextChunk::content)
                .toList();
    }

    public List<TextChunk> chunkPages(List<ExtractedPage> pages, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (pages == null) {
            return chunks;
        }

        for (ExtractedPage page : pages) {
            chunks.addAll(chunkTextWithPage(page.text(), page.pageNumber(), chunkSize, overlap));
        }

        return chunks;
    }

    private List<TextChunk> chunkTextWithPage(String text, Integer pageNumber, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                end = moveToNaturalBoundary(text, start, end);
            }

            String chunk = text.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            start = moveStartToNaturalBoundary(text, start, end, overlap);
        }

        return chunks.stream()
                .map(chunk -> new TextChunk(chunk, pageNumber))
                .toList();
    }

    private int moveToNaturalBoundary(String text, int start, int preferredEnd) {
        int minEnd = Math.min(text.length(), start + Math.max(1, (preferredEnd - start) / 2));
        for (int i = preferredEnd; i > minEnd; i--) {
            char current = text.charAt(i - 1);
            if (current == '.' || current == '!' || current == '?' || current == '\n') {
                return i;
            }
        }
        for (int i = preferredEnd; i > minEnd; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                return i;
            }
        }
        return preferredEnd;
    }

    private int moveStartToNaturalBoundary(String text, int previousStart, int previousEnd, int overlap) {
        int preferredStart = Math.max(previousEnd - overlap, previousStart + 1);
        if (preferredStart <= 0) {
            return 0;
        }

        int minStart = Math.max(previousStart + 1, preferredStart - START_BOUNDARY_SEARCH_WINDOW);
        int maxStart = Math.min(previousEnd, preferredStart + START_BOUNDARY_SEARCH_WINDOW);

        int sentenceStart = findSentenceStart(text, preferredStart, minStart, maxStart);
        if (sentenceStart >= 0) {
            return sentenceStart;
        }

        return moveToWordBoundary(text, preferredStart, minStart, maxStart);
    }

    private int findSentenceStart(String text, int preferredStart, int minStart, int maxStart) {
        for (int i = preferredStart; i >= minStart; i--) {
            if (isSentenceBoundary(text, i)) {
                return skipOpeningSeparators(text, i, maxStart);
            }
        }

        for (int i = preferredStart + 1; i <= maxStart; i++) {
            if (isSentenceBoundary(text, i)) {
                return skipOpeningSeparators(text, i, maxStart);
            }
        }

        return -1;
    }

    private boolean isSentenceBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) {
            return false;
        }

        char previous = text.charAt(index - 1);
        char current = text.charAt(index);
        return (previous == '.' || previous == '!' || previous == '?') && Character.isWhitespace(current);
    }

    private int moveToWordBoundary(String text, int preferredStart, int minStart, int maxStart) {
        int adjusted = Math.min(preferredStart, text.length() - 1);

        if (adjusted > minStart
                && adjusted < text.length()
                && isWordCharacter(text.charAt(adjusted - 1))
                && isWordCharacter(text.charAt(adjusted))) {
            while (adjusted > minStart && isWordCharacter(text.charAt(adjusted - 1))) {
                adjusted--;
            }
            return adjusted;
        }

        if (adjusted < text.length() && isOpeningSeparator(text.charAt(adjusted))) {
            return skipOpeningSeparators(text, adjusted, maxStart);
        }

        return adjusted;
    }

    private int skipOpeningSeparators(String text, int start, int maxStart) {
        int adjusted = start;
        int upperBound = Math.min(maxStart, text.length() - 1);
        while (adjusted <= upperBound && isOpeningSeparator(text.charAt(adjusted))) {
            adjusted++;
        }
        return Math.min(adjusted, text.length() - 1);
    }

    private boolean isOpeningSeparator(char value) {
        return Character.isWhitespace(value) || ",.;:!?)]}\"'".indexOf(value) >= 0;
    }

    private boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-';
    }
}
