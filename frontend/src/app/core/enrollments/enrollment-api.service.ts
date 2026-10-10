import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface EnrollmentContract {
  readonly title: string;
  readonly body: string;
  readonly status: string;
}

export interface EnrollmentServices {
  readonly transportSelected: boolean;
  readonly transportRoute: string | null;
  readonly cafeteriaSelected: boolean;
  readonly activities: readonly string[];
  readonly paymentPlan: string;
  readonly confirmed: boolean;
}

export interface EnrollmentDocument {
  readonly id: string;
  readonly documentType: string;
  readonly fileName: string;
  readonly createdAt: string;
}

export interface EnrollmentWorkflow {
  readonly id: string;
  readonly portalAccountId: string;
  readonly applicationId: string | null;
  readonly studentId: string | null;
  readonly academicYearId: string;
  readonly campusId: string;
  readonly classroomId: string | null;
  readonly workflowType: 'INITIAL' | 'RE_ENROLLMENT';
  readonly currentStep: string;
  readonly status: string;
  readonly registrationFee: number;
  readonly currencyCode: string;
  readonly contract: EnrollmentContract;
  readonly familyData: Readonly<Record<string, unknown>>;
  readonly familyConfirmed: boolean;
  readonly services: EnrollmentServices;
  readonly documents: readonly EnrollmentDocument[];
  readonly missingDocuments: readonly string[];
  readonly signed: boolean;
  readonly feeStatus: string;
  readonly activatedStudentId: string | null;
  readonly updatedAt: string;
}

export interface EnrollmentClass {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly capacity: number;
}

@Injectable({ providedIn: 'root' })
export class EnrollmentApiService {
  private readonly http = inject(HttpClient);
  private readonly publicBase = '/api/v1/public/enrollments';
  private readonly adminBase = '/api/v1/enrollments';
  private readonly credentials = { withCredentials: true } as const;

  portalWorkflows(): Observable<readonly EnrollmentWorkflow[]> {
    return this.http.get<readonly EnrollmentWorkflow[]>(this.publicBase, this.credentials);
  }

  startInitial(applicationId: string): Observable<EnrollmentWorkflow> {
    return this.http.post<EnrollmentWorkflow>(this.publicBase, { applicationId }, this.credentials);
  }

  acceptContract(workflowId: string): Observable<EnrollmentWorkflow> {
    return this.portalPost(`${workflowId}/contract/accept`, {});
  }

  confirmFamily(workflowId: string, data: Record<string, unknown>): Observable<EnrollmentWorkflow> {
    return this.http.put<EnrollmentWorkflow>(
      `${this.publicBase}/${workflowId}/family`,
      { data },
      this.credentials,
    );
  }

  confirmServices(
    workflowId: string,
    command: {
      transportSelected: boolean;
      transportRoute: string;
      cafeteriaSelected: boolean;
      activities: readonly string[];
      paymentPlan: string;
    },
  ): Observable<EnrollmentWorkflow> {
    return this.http.put<EnrollmentWorkflow>(
      `${this.publicBase}/${workflowId}/services`,
      command,
      this.credentials,
    );
  }

  upload(workflowId: string, documentType: string, file: File): Observable<EnrollmentWorkflow> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<EnrollmentWorkflow>(`${this.publicBase}/${workflowId}/documents`, body, {
      ...this.credentials,
      params: { documentType },
    });
  }

  sign(workflowId: string, signerName: string): Observable<EnrollmentWorkflow> {
    return this.portalPost(`${workflowId}/signature`, { signerName, accepted: true });
  }

  pay(workflowId: string): Observable<EnrollmentWorkflow> {
    return this.portalPost(`${workflowId}/fee-payments`, { cardLast4: '4242' });
  }

  adminWorkflows(): Observable<readonly EnrollmentWorkflow[]> {
    return this.http.get<readonly EnrollmentWorkflow[]>(this.adminBase);
  }

  classes(workflowId: string): Observable<readonly EnrollmentClass[]> {
    return this.http.get<readonly EnrollmentClass[]>(`${this.adminBase}/${workflowId}/classes`);
  }

  assignClass(workflowId: string, classroomId: string): Observable<EnrollmentWorkflow> {
    return this.http.patch<EnrollmentWorkflow>(`${this.adminBase}/${workflowId}/class`, {
      classroomId,
    });
  }

  activate(workflowId: string): Observable<EnrollmentWorkflow> {
    return this.http.post<EnrollmentWorkflow>(`${this.adminBase}/${workflowId}/activate`, {});
  }

  private portalPost(path: string, body: object): Observable<EnrollmentWorkflow> {
    return this.http.post<EnrollmentWorkflow>(`${this.publicBase}/${path}`, body, this.credentials);
  }
}
