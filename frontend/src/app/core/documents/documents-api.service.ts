import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type DocumentType =
  | 'PARENT_ID'
  | 'BIRTH_CERTIFICATE'
  | 'PHOTO'
  | 'CERTIFICATE'
  | 'CONTRACT'
  | 'AUTHORIZATION'
  | 'REPORT_CARD'
  | 'INVOICE'
  | 'ATTESTATION'
  | 'INTERNAL';

export type PdfTemplate =
  'SCHOOL_CERTIFICATE' | 'RECEIPT' | 'INVOICE' | 'CERTIFICATE' | 'REPORT_CARD' | 'CONTRACT';

export interface DocumentCategory {
  readonly id: string;
  readonly code: string;
  readonly labelFr: string;
  readonly labelAr: string | null;
  readonly systemCategory: boolean;
  readonly active: boolean;
}

export interface DocumentVersion {
  readonly id: string;
  readonly versionNumber: number;
  readonly fileName: string;
  readonly contentType: string;
  readonly sizeBytes: number;
  readonly sha256: string;
  readonly sourceKind: 'UPLOAD' | 'GENERATED';
  readonly pdfTemplate: PdfTemplate | null;
  readonly createdAt: string;
}

export interface SchoolDocument {
  readonly id: string;
  readonly documentType: DocumentType;
  readonly title: string;
  readonly description: string | null;
  readonly categoryCode: string;
  readonly categoryLabel: string;
  readonly ownerKind: string;
  readonly ownerReferenceId: string | null;
  readonly visibility: 'INTERNAL' | 'FAMILY' | 'PRIVATE';
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly expiresOn: string | null;
  readonly currentVersion: number;
  readonly versions: readonly DocumentVersion[];
  readonly sharedWithCurrentFamily: boolean;
  readonly updatedAt: string;
}

export interface ParentOption {
  readonly id: string;
  readonly displayName: string;
  readonly email: string;
}

export interface DocumentDashboard {
  readonly generatedAt: string;
  readonly documentTypes: readonly DocumentType[];
  readonly pdfTemplates: readonly PdfTemplate[];
  readonly categories: readonly DocumentCategory[];
  readonly documents: readonly SchoolDocument[];
  readonly parents: readonly ParentOption[];
  readonly storage: Readonly<Record<string, string>>;
}

export interface FamilyDocumentDashboard {
  readonly generatedAt: string;
  readonly parentName: string;
  readonly documents: readonly SchoolDocument[];
  readonly storage: Readonly<Record<string, string>>;
}

export interface SignedDocumentUrl {
  readonly url: string;
  readonly expiresAt: string;
  readonly maxDownloads: number;
  readonly sha256: string;
  readonly fileName: string;
}

@Injectable({ providedIn: 'root' })
export class DocumentsApiService {
  private readonly http = inject(HttpClient);
  private readonly internalUrl = '/api/v1/documents';
  private readonly familyUrl = '/api/v1/public/documents';

  dashboard(): Observable<DocumentDashboard> {
    return this.http.get<DocumentDashboard>(`${this.internalUrl}/dashboard`);
  }

  familyDashboard(): Observable<FamilyDocumentDashboard> {
    return this.http.get<FamilyDocumentDashboard>(this.familyUrl);
  }

  generate(command: {
    categoryId: string;
    template: PdfTemplate;
    title: string;
    description: string;
    recipient: string;
    reference: string;
    ownerKind: string;
    ownerReferenceId: string | null;
    visibility: string;
    expiresOn: string | null;
    fields: Readonly<Record<string, string>>;
  }): Observable<SchoolDocument> {
    return this.http.post<SchoolDocument>(`${this.internalUrl}/generated`, command);
  }

  upload(
    metadata: {
      categoryId: string;
      documentType: DocumentType;
      title: string;
      description: string;
      ownerKind: string;
      ownerReferenceId: string | null;
      visibility: string;
      expiresOn: string | null;
    },
    file: File,
  ): Observable<SchoolDocument> {
    const form = new FormData();
    Object.entries(metadata).forEach(([key, value]) => {
      if (value !== null && value !== '') form.set(key, value);
    });
    form.set('file', file, file.name);
    return this.http.post<SchoolDocument>(this.internalUrl, form);
  }

  addVersion(documentId: string, file: File): Observable<SchoolDocument> {
    const form = new FormData();
    form.set('file', file, file.name);
    return this.http.post<SchoolDocument>(`${this.internalUrl}/${documentId}/versions`, form);
  }

  share(documentId: string, portalAccountId: string): Observable<SchoolDocument> {
    return this.http.put<SchoolDocument>(`${this.internalUrl}/${documentId}/family-share`, {
      portalAccountId,
      canDownload: true,
      validUntil: null,
    });
  }

  signedUrl(documentId: string): Observable<SignedDocumentUrl> {
    return this.http.post<SignedDocumentUrl>(`${this.internalUrl}/${documentId}/signed-url`, null);
  }

  familySignedUrl(documentId: string): Observable<SignedDocumentUrl> {
    return this.http.post<SignedDocumentUrl>(`${this.familyUrl}/${documentId}/signed-url`, null);
  }
}
