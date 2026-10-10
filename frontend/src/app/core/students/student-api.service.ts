import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface StudentSummary {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly preferredName: string | null;
  readonly birthDate: string;
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly photoObjectKey: string | null;
  readonly schoolIdentifier: string;
  readonly campusName: string | null;
  readonly classroomName: string | null;
  readonly academicYearName: string | null;
}

export interface StudentPage {
  readonly page: number;
  readonly size: number;
  readonly hasNext: boolean;
  readonly items: readonly StudentSummary[];
}

export interface StudentIdentity extends Omit<
  StudentSummary,
  'schoolIdentifier' | 'campusName' | 'classroomName' | 'academicYearName'
> {
  readonly personId: string;
  readonly gender: string | null;
  readonly nationalityCode: string | null;
  readonly archivedAt: string | null;
}

export interface AddressView {
  readonly type: string;
  readonly line1: string;
  readonly line2: string | null;
  readonly city: string;
  readonly region: string | null;
  readonly postalCode: string | null;
  readonly countryCode: string;
  readonly primary: boolean;
}

export interface GuardianView {
  readonly id: string;
  readonly personId: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly relationship: string;
  readonly email: string | null;
  readonly phone: string | null;
  readonly occupation: string | null;
  readonly legalGuardian: boolean;
  readonly financialResponsible: boolean;
}

export interface EmergencyContactView {
  readonly id: string;
  readonly displayName: string;
  readonly relationship: string;
  readonly phone: string;
  readonly priority: number;
  readonly authorizedPickup: boolean;
}

export interface EnrollmentView {
  readonly id: string;
  readonly status: string;
  readonly enrolledOn: string;
  readonly endedOn: string | null;
  readonly campusId: string;
  readonly campusName: string;
  readonly academicYearId: string;
  readonly academicYearName: string;
  readonly classroomId: string | null;
  readonly classroomName: string | null;
  readonly gradeLevelId: string | null;
  readonly gradeLevelName: string | null;
}

export interface IdentifierView {
  readonly id: string;
  readonly type: string;
  readonly value: string;
  readonly issuer: string | null;
  readonly primary: boolean;
}

export interface DocumentView {
  readonly id: string;
  readonly type: string;
  readonly title: string;
  readonly objectKey: string;
  readonly status: string;
  readonly expiresOn: string | null;
  readonly createdAt: string;
}

export interface HistoryView {
  readonly id: string;
  readonly type: string;
  readonly summary: string;
  readonly details: string | null;
  readonly occurredAt: string;
  readonly actorUserId: string | null;
}

export interface SectionAvailability {
  readonly status: 'PENDING_MODULE' | 'AVAILABLE';
  readonly module: string;
}

export interface StudentRecord {
  readonly identity: StudentIdentity;
  readonly addresses: readonly AddressView[];
  readonly guardians: readonly GuardianView[];
  readonly emergencyContacts: readonly EmergencyContactView[];
  readonly enrollments: readonly EnrollmentView[];
  readonly identifiers: readonly IdentifierView[];
  readonly customFields: Readonly<Record<string, string>>;
  readonly documents: readonly DocumentView[];
  readonly attendance: readonly unknown[];
  readonly grades: readonly unknown[];
  readonly finance: SectionAvailability;
  readonly transport: SectionAvailability;
  readonly health: SectionAvailability;
  readonly history: readonly HistoryView[];
}

@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);

  students(search = '', page = 0, size = 40) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) params = params.set('search', search);
    return this.http.get<StudentPage>('/api/v1/students/page', { params });
  }

  student(studentId: string) {
    return this.http.get<StudentRecord>(`/api/v1/students/${studentId}`);
  }

  archive(studentId: string, reason: string) {
    return this.http.patch<StudentRecord>(`/api/v1/students/${studentId}/archive`, { reason });
  }
}
