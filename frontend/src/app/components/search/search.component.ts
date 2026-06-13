import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../services/document.service';
import { Document } from '../../models/document.model';

@Component({
    selector: 'app-search',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './search.component.html',
    styleUrls: ['./search.component.css']
})
export class SearchComponent {
    // Manual Entry
    manualFileName = '';
    manualContent = '';

    // File Upload
    selectedFile: File | null = null;

    // Search & UI
    query = '';
    results: Document[] = [];
    status = '';
    isProcessing = false;

    constructor(private documentService: DocumentService) {}

    // Fixed the FileList vs File issue and added null safety
    onFileSelected(event: any): void {
        const element = event.currentTarget as HTMLInputElement;
        const fileList: FileList | null = element.files;

        if (fileList && fileList.length > 0) {
            // FIX: Access the first element of the list using
            const file: File = fileList[0];

            this.selectedFile = file;
            this.status = `Selected: ${file.name}`;

            // Reset manual fields when a file is chosen
            this.manualFileName = '';
            this.manualContent = '';

            console.log('[LOG] File captured:', file.name, 'Size:', file.size);
        } else {
            this.selectedFile = null;
            this.status = '❌ No file selected.';
        }
    }

    onUpload(): void {
        if (!this.selectedFile && (!this.manualFileName || !this.manualContent)) {
            this.status = '❌ Please select a file OR enter name and content manually.';
            return;
        }

        this.isProcessing = true;
        this.status = 'Uploading...';

        if (this.selectedFile) {
            console.log('[LOG] Sending file via Multipart:', this.selectedFile.name);
            this.documentService.uploadFile(this.selectedFile).subscribe({
                next: (res) => this.handleSuccess(res),
                error: (err) => this.handleError(err) // Now accepts the error
            });
        } else {
            console.log('[LOG] Sending manual text entry');
            this.documentService.upload(this.manualFileName, this.manualContent).subscribe({
                next: (res) => this.handleSuccess(res),
                error: (err) => this.handleError(err)
            });
        }
    }

    private handleSuccess(res: Document) {
        this.status = `✅ Success! Uploaded: ${res.fileName}`;
        this.manualFileName = '';
        this.manualContent = '';
        this.selectedFile = null;
        this.isProcessing = false;
    }

    // Updated to accept an optional error argument to fix TS2554
    private handleError(err?: any) {
        if (err) {
            console.error('[LOG] Upload Error Details:', err);
        }
        this.status = '❌ Error during upload. Check backend.';
        this.isProcessing = false;
    }

    onSearch(): void {
        if (!this.query.trim()) return;
        this.isProcessing = true;
        this.documentService.search(this.query).subscribe({
            next: (data) => {
                this.results = data;
                this.status = `Found ${data.length} results.`;
                this.isProcessing = false;
            },
            error: (err) => {
                console.error('[LOG] Search Error:', err);
                this.status = '❌ Search failed.';
                this.isProcessing = false;
            }
        });
    }
}
