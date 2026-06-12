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
    fileName = '';
    content = '';
    query = '';
    results: Document[] = [];
    status = '';

    constructor(private service: DocumentService) {}

    onUpload() {
        this.status = 'Uploading...';
        this.service.upload(this.fileName, this.content).subscribe({
            next: () => { this.status = '✅ Success!'; this.fileName = ''; this.content = ''; },
            error: () => this.status = '❌ Upload failed. Check console.'
        });
    }

    onSearch() {
        this.status = 'Searching...';
        this.service.search(this.query).subscribe({
            next: (data) => { this.results = data; this.status = `Found ${data.length} results.`; },
            error: () => this.status = '❌ Search failed.'
        });
    }
}
