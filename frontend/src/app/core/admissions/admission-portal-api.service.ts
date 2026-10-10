import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AdmissionCampaign, AdmissionStage } from './admission-api.service';

export interface ReferenceOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

export interface AdmissionCatalog {
  readonly tenantSlug: string;
  readonly schoolName: string;
  readonly campaigns: readonly AdmissionCampaign[];
  readonly campuses: readonly ReferenceOption[];
  readonly grades: readonly ReferenceOption[];
}

export interface PortalAccount {
  readonly tenantId: string;
  readonly accountId: string;
  readonly email: string;
  readonly displayName: string;
  readonly locale: string;
}

export interface Applicant {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly birthDate: string;
  readonly gender: string | null;
  readonly nationalityCode: string | null;
  readonly currentSchool: string | null;
}

export interface AdmissionDocument {
  readonly id: string;
  readonly documentType: string;
  readonly originalFileName: string;
  readonly contentType: string;
  readonly sizeBytes: number;
  readonly status: string;
  readonly rejectionReason: string | null;
  readonly createdAt: string;
}

export interface AdmissionOffer {
  readonly id: string;
  readonly reference: string;
  readonly offeredOn: string;
  readonly expiresOn: string;
  readonly status: string;
  readonly conditions: string | null;
}

export interface PortalApplication {
  readonly id: string;
  readonly referenceNumber: string;
  readonly applicantId: string;
  readonly applicantName: string;
  readonly campaignId: string;
  readonly campaignName: string;
  readonly status: AdmissionStage;
  readonly completenessPercent: number;
  readonly formData: Readonly<Record<string, unknown>>;
  readonly applicationFee: number;
  readonly currencyCode: string;
  readonly paymentStatus: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly documents: readonly AdmissionDocument[];
  readonly history: readonly {
    readonly fromStatus: string | null;
    readonly toStatus: string;
    readonly comment: string | null;
    readonly occurredAt: string;
  }[];
  readonly offer: AdmissionOffer | null;
}

export interface PortalNotification {
  readonly id: string;
  readonly applicationId: string | null;
  readonly type: string;
  readonly title: string;
  readonly message: string;
  readonly readAt: string | null;
  readonly createdAt: string;
}

export interface PortalHome {
  readonly account: PortalAccount;
  readonly applicants: readonly Applicant[];
  readonly applications: readonly PortalApplication[];
  readonly notifications: readonly PortalNotification[];
}

@Injectable({ providedIn: 'root' })
export class AdmissionPortalApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/public/admissions';
  private readonly options = { withCredentials: true } as const;

  catalog(tenantSlug: string): Observable<AdmissionCatalog> {
    return this.http.get<AdmissionCatalog>(`${this.base}/${tenantSlug}/catalog`);
  }

  register(command: {
    tenantSlug: string;
    email: string;
    password: string;
    displayName: string;
    phone: string;
  }): Observable<PortalAccount> {
    return this.http.post<PortalAccount>(`${this.base}/accounts`, command, this.options);
  }

  login(command: {
    tenantSlug: string;
    email: string;
    password: string;
  }): Observable<PortalAccount> {
    return this.http.post<PortalAccount>(`${this.base}/sessions`, command, this.options);
  }

  logout(): Observable<void> {
    return this.http.delete<void>(`${this.base}/session`, this.options);
  }

  home(): Observable<PortalHome> {
    return this.http.get<PortalHome>(`${this.base}/me`, this.options);
  }

  addApplicant(command: {
    firstName: string;
    lastName: string;
    birthDate: string;
    gender: string;
    nationalityCode: string;
    currentSchool: string;
  }): Observable<Applicant> {
    return this.http.post<Applicant>(`${this.base}/applicants`, command, this.options);
  }

  createApplication(command: {
    campaignId: string;
    applicantId: string;
    desiredCampusId: string;
    desiredGradeLevelId: string;
  }): Observable<PortalApplication> {
    return this.http.post<PortalApplication>(`${this.base}/applications`, command, this.options);
  }

  saveForm(
    applicationId: string,
    formData: Record<string, unknown>,
  ): Observable<PortalApplication> {
    return this.http.put<PortalApplication>(
      `${this.base}/applications/${applicationId}`,
      { formData },
      this.options,
    );
  }

  uploadDocument(
    applicationId: string,
    documentType: string,
    file: File,
  ): Observable<AdmissionDocument> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<AdmissionDocument>(
      `${this.base}/applications/${applicationId}/documents`,
      body,
      { ...this.options, params: { documentType } },
    );
  }

  submit(applicationId: string): Observable<PortalApplication> {
    return this.http.post<PortalApplication>(
      `${this.base}/applications/${applicationId}/submit`,
      {},
      this.options,
    );
  }

  pay(applicationId: string, cardLast4: string): Observable<PortalApplication> {
    return this.http.post<PortalApplication>(
      `${this.base}/applications/${applicationId}/fee-payments`,
      { cardLast4 },
      this.options,
    );
  }

  acceptOffer(offerId: string): Observable<PortalApplication> {
    return this.http.post<PortalApplication>(
      `${this.base}/offers/${offerId}/accept`,
      {},
      this.options,
    );
  }

  markRead(notificationId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/notifications/${notificationId}/read`,
      {},
      this.options,
    );
  }
}
