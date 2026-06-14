import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { DocumentService } from '../../services/document.service';
import {
    Chunk,
    DocumentSearchResult,
    DocumentStats,
    DocumentSummary,
    SearchResult
} from '../../models/document.model';

type StatusType = 'info' | 'success' | 'error';

@Component({
    selector: 'app-search',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './search.component.html',
    styleUrls: ['./search.component.css']
})
export class SearchComponent implements OnInit, OnDestroy {
    manualFileName = '';
    manualContent = '';
    selectedFile: File | null = null;

    query = '';
    mode: 'semantic' | 'keyword' | 'hybrid' = 'semantic';
    limit = 5;
    selectedFilterDocumentId: number | null = null;
    aggregateByDocument = false;
    rerankingEnabled = true;
    results: SearchResult[] = [];
    groupedResults: DocumentSearchResult[] = [];

    stats: DocumentStats | null = null;
    documents: DocumentSummary[] = [];
    selectedDocument: DocumentSummary | null = null;
    selectedChunks: Chunk[] = [];

    toastMessage = '';
    toastType: StatusType = 'info';
    private toastTimer: ReturnType<typeof setTimeout> | null = null;
    isUploading = false;
    isSearching = false;
    isLoadingDashboard = false;
    isLoadingChunks = false;

    readonly searchModes = [
        { value: 'semantic', label: 'Semantic' },
        { value: 'keyword', label: 'Keyword' },
        { value: 'hybrid', label: 'Hybrid' }
    ] as const;

    constructor(private documentService: DocumentService) {}

    ngOnInit(): void {
        this.refreshDashboard();
    }

    ngOnDestroy(): void {
        if (this.toastTimer) {
            clearTimeout(this.toastTimer);
        }
    }

    onFileSelected(event: Event): void {
        const input = event.currentTarget as HTMLInputElement;
        const file = input.files?.item(0) ?? null;
        this.selectedFile = file;

        if (file) {
            this.manualFileName = '';
            this.manualContent = '';
            this.setStatus(`Selected ${file.name}`, 'info');
        } else {
            this.setStatus('No file selected.', 'error');
        }
    }

    onUpload(): void {
        const hasManualDocument = this.manualFileName.trim() && this.manualContent.trim();
        if (!this.selectedFile && !hasManualDocument) {
            this.setStatus('Choose a PDF/TXT/MD file or add manual text first.', 'error');
            return;
        }

        this.isUploading = true;
        this.setStatus('Indexing document and generating embeddings...', 'info');

        const request$ = this.selectedFile
            ? this.documentService.uploadFile(this.selectedFile)
            : this.documentService.uploadManual(this.manualFileName.trim(), this.manualContent.trim());

        request$
            .pipe(finalize(() => (this.isUploading = false)))
            .subscribe({
                next: (document) => {
                    this.setStatus(`Indexed ${document.fileName} into ${document.chunkCount} chunks.`, 'success');
                    this.manualFileName = '';
                    this.manualContent = '';
                    this.selectedFile = null;
                    this.refreshDashboard();
                },
                error: (error) => this.setStatus(this.extractError(error, 'Upload failed.'), 'error')
            });
    }

    onSearch(): void {
        if (!this.query.trim()) {
            this.setStatus('Add a search query first.', 'error');
            return;
        }

        this.isSearching = true;
        this.results = [];
        this.groupedResults = [];
        this.setStatus(`Running ${this.mode} search...`, 'info');

        if (this.aggregateByDocument) {
            this.documentService.searchByDocument(
                this.query.trim(),
                this.mode,
                this.limit,
                this.selectedFilterDocumentId,
                this.rerankingEnabled
            )
                .pipe(finalize(() => (this.isSearching = false)))
                .subscribe({
                    next: (results: DocumentSearchResult[]) => {
                        this.groupedResults = results;
                        this.setStatus(`Found ${results.length} result${results.length === 1 ? '' : 's'}.`, 'success');
                    },
                    error: (error: any) => this.setStatus(this.extractError(error, 'Search failed.'), 'error')
                });
            return;
        }

        this.documentService.search(
            this.query.trim(),
            this.mode,
            this.limit,
            this.selectedFilterDocumentId,
            this.rerankingEnabled
        )
            .pipe(finalize(() => (this.isSearching = false)))
            .subscribe({
                next: (results: SearchResult[]) => {
                    this.results = results;
                    this.setStatus(`Found ${results.length} result${results.length === 1 ? '' : 's'}.`, 'success');
                },
                error: (error: any) => this.setStatus(this.extractError(error, 'Search failed.'), 'error')
            });
    }

    refreshDashboard(): void {
        this.isLoadingDashboard = true;
        this.documentService.getStats().subscribe({
            next: (stats) => (this.stats = stats),
            error: () => this.setStatus('Could not load statistics. Is the backend running?', 'error')
        });

        this.documentService.getDocuments()
            .pipe(finalize(() => (this.isLoadingDashboard = false)))
            .subscribe({
                next: (documents) => (this.documents = documents),
                error: () => this.setStatus('Could not load document list. Is the backend running?', 'error')
            });
    }

    viewChunks(document: DocumentSummary): void {
        this.selectedDocument = document;
        this.selectedChunks = [];
        this.isLoadingChunks = true;

        this.documentService.getChunks(document.id)
            .pipe(finalize(() => (this.isLoadingChunks = false)))
            .subscribe({
                next: (chunks) => (this.selectedChunks = chunks),
                error: (error) => this.setStatus(this.extractError(error, 'Could not load chunks.'), 'error')
            });
    }

    deleteDocument(document: DocumentSummary): void {
        const shouldDelete = window.confirm(`Delete ${document.fileName} and all its chunks?`);
        if (!shouldDelete) {
            return;
        }

        this.documentService.deleteDocument(document.id).subscribe({
            next: () => {
                this.setStatus(`Deleted ${document.fileName}.`, 'success');
                if (this.selectedDocument?.id === document.id) {
                    this.selectedDocument = null;
                    this.selectedChunks = [];
                }
                this.refreshDashboard();
            },
            error: (error) => this.setStatus(this.extractError(error, 'Delete failed.'), 'error')
        });
    }

    formatScore(score: number | null | undefined): string {
        return `${Math.round((score ?? 0) * 100)}%`;
    }

    formatNumber(value: number | null | undefined): string {
        return new Intl.NumberFormat('en-US').format(value ?? 0);
    }

    formatFileSize(bytes: number | null | undefined): string {
        if (!bytes) {
            return 'n/a';
        }
        if (bytes < 1024) {
            return `${bytes} B`;
        }
        if (bytes < 1024 * 1024) {
            return `${(bytes / 1024).toFixed(1)} KB`;
        }
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    pageLabel(pageNumber: number | null | undefined): string {
        return pageNumber ? `pag. ${pageNumber}` : 'fara pagina';
    }

    displayTitle(document: Pick<DocumentSummary, 'fileName' | 'pdfTitle'>): string {
        return document.pdfTitle || document.fileName;
    }

    trackById(_: number, item: { id?: number; chunkId?: number; documentId?: number }): number | undefined {
        return item.id ?? item.chunkId ?? item.documentId;
    }

    chunkContentHtml(chunk: Chunk, index: number): string {
        const content = chunk.content ?? '';
        const overlapLength = this.detectOverlapLength(content, index);
        const escapedContent = this.escapeHtml(content);

        if (overlapLength <= 0) {
            return escapedContent;
        }

        const escapedOverlap = this.escapeHtml(content.slice(0, overlapLength));
        const escapedRest = this.escapeHtml(content.slice(overlapLength));
        return `<span class="overlap-fragment">${escapedOverlap}</span>${escapedRest}`;
    }

    private setStatus(message: string, type: StatusType): void {
        this.toastMessage = message;
        this.toastType = type;

        if (this.toastTimer) {
            clearTimeout(this.toastTimer);
        }

        this.toastTimer = setTimeout(() => {
            this.toastMessage = '';
            this.toastTimer = null;
        }, type === 'info' ? 2600 : 4200);
    }

    private extractError(error: any, fallback: string): string {
        return error?.error?.detail ?? error?.message ?? fallback;
    }

    private detectOverlapLength(content: string, index: number): number {
        if (index <= 0 || !this.selectedChunks[index - 1]?.content || !content) {
            return 0;
        }

        const previous = this.selectedChunks[index - 1].content;
        const maxLength = Math.min(260, previous.length, content.length);
        const minLength = 24;

        for (let length = maxLength; length >= minLength; length--) {
            const prefix = content.slice(0, length);
            if (previous.endsWith(prefix)) {
                return length;
            }
        }

        return 0;
    }

    private escapeHtml(value: string): string {
        return value
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
}
