import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Chunk, DocumentSearchResult, DocumentStats, DocumentSummary, SearchResult } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
    private readonly apiUrl = 'http://localhost:8081/api/documents';

    constructor(private http: HttpClient) {}

    uploadManual(fileName: string, content: string): Observable<DocumentSummary> {
        const params = new HttpParams().set('fileName', fileName);
        const headers = new HttpHeaders({ 'Content-Type': 'text/plain; charset=utf-8' });
        return this.http.post<DocumentSummary>(`${this.apiUrl}/upload`, content, { params, headers });
    }

    uploadFile(file: File): Observable<DocumentSummary> {
        const formData = new FormData();
        formData.append('file', file, file.name);
        return this.http.post<DocumentSummary>(`${this.apiUrl}/upload-file`, formData);
    }

    search(
        query: string,
        mode: string,
        limit: number,
        documentId: number | null,
        rerank: boolean
    ): Observable<SearchResult[]> {
        let params = new HttpParams()
            .set('query', query)
            .set('mode', mode)
            .set('limit', limit.toString())
            .set('rerank', rerank.toString());

        if (documentId) {
            params = params.set('documentId', documentId.toString());
        }

        return this.http.get<SearchResult[]>(`${this.apiUrl}/search`, { params });
    }

    searchByDocument(
        query: string,
        mode: string,
        limit: number,
        documentId: number | null,
        rerank: boolean
    ): Observable<DocumentSearchResult[]> {
        let params = new HttpParams()
            .set('query', query)
            .set('mode', mode)
            .set('limit', limit.toString())
            .set('rerank', rerank.toString());

        if (documentId) {
            params = params.set('documentId', documentId.toString());
        }

        return this.http.get<DocumentSearchResult[]>(`${this.apiUrl}/search/documents`, { params });
    }

    getStats(): Observable<DocumentStats> {
        return this.http.get<DocumentStats>(`${this.apiUrl}/stats`);
    }

    getDocuments(): Observable<DocumentSummary[]> {
        return this.http.get<DocumentSummary[]>(this.apiUrl);
    }

    getChunks(documentId: number): Observable<Chunk[]> {
        return this.http.get<Chunk[]>(`${this.apiUrl}/${documentId}/chunks`);
    }

    deleteDocument(documentId: number): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${documentId}`);
    }
}
