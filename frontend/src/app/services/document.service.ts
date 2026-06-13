import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { Document } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
    // Corrected: Removed the citation tag
    private apiUrl = 'http://localhost:8081/api/documents';

    constructor(private http: HttpClient) {}

    upload(fileName: string, content: string): Observable<Document> {
        console.log(`[LOG] Attempting upload for: ${fileName}`);
        const params = new HttpParams().set('fileName', fileName);
        return this.http.post<Document>(`${this.apiUrl}/upload`, content, { params }).pipe(
            tap(res => console.log('[LOG] Upload successful:', res)),
            catchError(err => {
                console.error('[LOG] Upload error:', err);
                return throwError(() => err);
            })
        );
    }

    search(query: string, limit: number = 1): Observable<Document[]> {
        console.log(`[LOG] Searching for: "${query}" (limit: ${limit})`);
        const params = new HttpParams()
            .set('query', query)
            .set('limit', limit.toString());

        return this.http.get<Document[]>(`${this.apiUrl}/search`, { params }).pipe(
            tap(res => console.log(`[LOG] Search success. Found ${res.length} items.`)),
            catchError(err => {
                console.error('[LOG] Search error:', err);
                return throwError(() => err);
            })
        );
    }

    uploadFile(file: File): Observable<Document> {
        const formData = new FormData();
        // The key 'file' MUST match @RequestParam("file") in your Java Controller
        formData.append('file', file, file.name);

        return this.http.post<Document>(`${this.apiUrl}/upload-file`, formData).pipe(
            tap(res => console.log('[LOG] Server Response:', res)),
            catchError(err => throwError(() => err))
        );
    }
}
