import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface PrivacyPurpose {
  readonly id: string;
  readonly purposeCode: string;
  readonly name: string;
  readonly description: string;
  readonly lawfulBasis: string;
  readonly dataCategories: string;
  readonly recipients: string | null;
  readonly retentionDays: number;
  readonly sensitive: boolean;
  readonly active: boolean;
}

export interface PrivacyRequest {
  readonly id: string;
  readonly requestNumber: string;
  readonly requestType: string;
  readonly subjectType: string;
  readonly subjectReference: string;
  readonly status: string;
  readonly receivedAt: string;
  readonly dueAt: string;
  readonly resolvedAt: string | null;
  readonly resolutionAction: string | null;
  readonly notes: string | null;
}

export interface RetentionPolicy {
  readonly id: string;
  readonly dataCategory: string;
  readonly retentionDays: number;
  readonly expiryAction: string;
  readonly legalHold: boolean;
  readonly lastReviewedAt: string;
}

export interface PrivacyExport {
  readonly id: string;
  readonly requestId: string | null;
  readonly subjectType: string;
  readonly subjectReference: string;
  readonly scopeSummary: string;
  readonly status: string;
  readonly createdAt: string;
  readonly expiresAt: string;
}

export interface HostingConfiguration {
  countryCode: string;
  regionLabel: string;
  providerName: string;
  encryptionAtRest: boolean;
  encryptionInTransit: boolean;
  crossBorderTransfer: boolean;
  legalValidationStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  readonly updatedAt?: string;
}

export interface PrivacyDashboard {
  readonly generatedAt: string;
  readonly summary: {
    readonly openRequests: number;
    readonly overdueRequests: number;
    readonly purposes: number;
    readonly consentEvents: number;
  };
  readonly purposes: readonly PrivacyPurpose[];
  readonly requests: readonly PrivacyRequest[];
  readonly retention: readonly RetentionPolicy[];
  readonly exports: readonly PrivacyExport[];
  readonly hosting: HostingConfiguration | null;
}

export interface PrivacyRequestCommand {
  requestType: string;
  subjectType: string;
  subjectReference: string;
  notes: string;
}

@Injectable({ providedIn: 'root' })
export class PrivacyApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<PrivacyDashboard>('/api/v1/privacy');
  }

  createRequest(command: PrivacyRequestCommand) {
    return this.http.post<PrivacyRequest>('/api/v1/privacy/requests', command);
  }

  updateRequest(requestId: string, status: string, resolutionAction?: string, notes?: string) {
    return this.http.put<PrivacyRequest>(`/api/v1/privacy/requests/${requestId}/status`, {
      status,
      resolutionAction,
      notes,
    });
  }

  configureRetention(command: {
    dataCategory: string;
    retentionDays: number;
    expiryAction: string;
    legalHold: boolean;
  }) {
    return this.http.put<void>('/api/v1/privacy/retention', command);
  }

  prepareExport(request: PrivacyRequest) {
    return this.http.post<PrivacyExport>('/api/v1/privacy/exports', {
      requestId: request.id,
      subjectType: request.subjectType,
      subjectReference: request.subjectReference,
      scopeSummary: 'Données personnelles rattachées au dossier vérifié',
    });
  }

  configureHosting(command: HostingConfiguration) {
    return this.http.put<HostingConfiguration>('/api/v1/privacy/hosting', command);
  }
}
