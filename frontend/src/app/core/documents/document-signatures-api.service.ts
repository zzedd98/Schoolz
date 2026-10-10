import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type SignatureStatus = 'GENERATED' | 'SENT' | 'VIEWED' | 'ACCEPTED' | 'SIGNED';

export interface SignatureEvent {
  readonly id: string;
  readonly fromStatus: SignatureStatus | null;
  readonly toStatus: SignatureStatus;
  readonly actorKind: 'PARENT' | 'USER' | 'SYSTEM';
  readonly actorName: string;
  readonly ipAddress: string | null;
  readonly providerKey: string | null;
  readonly providerReference: string | null;
  readonly evidenceSha256: string | null;
  readonly occurredAt: string;
}

export interface SignatureWorkflow {
  readonly id: string;
  readonly documentId: string;
  readonly documentVersionId: string;
  readonly versionNumber: number;
  readonly documentTitle: string;
  readonly documentType: string;
  readonly fileName: string;
  readonly documentSha256: string;
  readonly status: SignatureStatus;
  readonly recipientName: string;
  readonly recipientEmail: string | null;
  readonly legalNoticeVersion: string;
  readonly providerKey: string | null;
  readonly providerReference: string | null;
  readonly assuranceLevel: string | null;
  readonly generatedAt: string;
  readonly sentAt: string | null;
  readonly viewedAt: string | null;
  readonly acceptedAt: string | null;
  readonly signedAt: string | null;
  readonly signatoryName: string | null;
  readonly signatoryIp: string | null;
  readonly evidenceSha256: string | null;
  readonly events: readonly SignatureEvent[];
}

export interface SignatureDocumentOption {
  readonly id: string;
  readonly title: string;
  readonly documentType: string;
  readonly currentVersion: number;
  readonly sha256: string;
}

export interface SignatureParentOption {
  readonly id: string;
  readonly displayName: string;
  readonly email: string;
}

export interface SignatureProvider {
  readonly key: string;
  readonly assuranceLevel: string;
  readonly qualified: boolean;
  readonly description: string;
}

export interface SignatureDashboard {
  readonly generatedAt: string;
  readonly workflows: readonly SignatureWorkflow[];
  readonly documents: readonly SignatureDocumentOption[];
  readonly parents: readonly SignatureParentOption[];
  readonly providers: readonly SignatureProvider[];
  readonly legalNotice: Readonly<Record<string, string>>;
}

export interface FamilySignatureDashboard {
  readonly generatedAt: string;
  readonly parentName: string;
  readonly workflows: readonly SignatureWorkflow[];
  readonly legalNotice: Readonly<Record<string, string>>;
}

@Injectable({ providedIn: 'root' })
export class DocumentSignaturesApiService {
  private readonly http = inject(HttpClient);
  private readonly internalUrl = '/api/v1/document-signatures';
  private readonly familyUrl = '/api/v1/public/document-signatures';

  dashboard(): Observable<SignatureDashboard> {
    return this.http.get<SignatureDashboard>(`${this.internalUrl}/dashboard`);
  }

  create(documentId: string, portalAccountId: string): Observable<SignatureWorkflow> {
    return this.http.post<SignatureWorkflow>(this.internalUrl, {
      documentId,
      portalAccountId,
    });
  }

  send(workflowId: string): Observable<SignatureWorkflow> {
    return this.http.post<SignatureWorkflow>(`${this.internalUrl}/${workflowId}/send`, null);
  }

  familyDashboard(): Observable<FamilySignatureDashboard> {
    return this.http.get<FamilySignatureDashboard>(this.familyUrl);
  }

  view(workflowId: string): Observable<SignatureWorkflow> {
    return this.http.post<SignatureWorkflow>(`${this.familyUrl}/${workflowId}/view`, null);
  }

  accept(workflowId: string): Observable<SignatureWorkflow> {
    return this.http.post<SignatureWorkflow>(`${this.familyUrl}/${workflowId}/accept`, {
      consent: true,
    });
  }

  sign(workflowId: string, signatoryName: string): Observable<SignatureWorkflow> {
    return this.http.post<SignatureWorkflow>(`${this.familyUrl}/${workflowId}/sign`, {
      signatoryName,
      providerKey: 'local-simple',
      confirmSimpleSignature: true,
    });
  }
}
