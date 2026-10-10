import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface HealthPolicy {
  readonly vaccinationTrackingEnabled: boolean;
  readonly updatedAt: string | null;
}

export interface HealthStudentSummary {
  readonly studentId: string;
  readonly studentName: string;
  readonly profileReady: boolean;
  readonly activeAllergies: number;
  readonly activeMedications: number;
  readonly openIncidents: number;
  readonly lastVisitAt: string | null;
}

export interface HealthEmergencyAction {
  readonly id: string;
  readonly actionType: string;
  readonly description: string;
  readonly performedAt: string;
  readonly guardianContacted: boolean;
  readonly externalReference: string | null;
}

export interface HealthIncident {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly occurredAt: string;
  readonly incidentType: string;
  readonly severity: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL';
  readonly location: string | null;
  readonly description: string;
  readonly actionTaken: string | null;
  readonly guardianNotifiedAt: string | null;
  readonly status: 'OPEN' | 'MONITORING' | 'CLOSED' | 'TRANSFERRED';
  readonly emergencyActions: readonly HealthEmergencyAction[];
}

export interface HealthDashboard {
  readonly generatedAt: string;
  readonly safetyNotice: string;
  readonly policy: HealthPolicy;
  readonly students: readonly HealthStudentSummary[];
  readonly recentIncidents: readonly HealthIncident[];
}

export interface HealthProfile {
  readonly id: string | null;
  readonly bloodGroup: string | null;
  readonly insuranceProvider: string | null;
  readonly insuranceNumber: string | null;
  readonly physicianName: string | null;
  readonly physicianPhone: string | null;
  readonly emergencyNotes: string | null;
  readonly updatedAt: string | null;
  readonly version: number;
}

export interface HealthAllergy {
  readonly id: string;
  readonly allergen: string;
  readonly reactionDescription: string | null;
  readonly severity: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL';
  readonly emergencyInstructions: string | null;
  readonly active: boolean;
  readonly createdAt: string;
}

export interface HealthMedication {
  readonly id: string;
  readonly medicationName: string;
  readonly dosageText: string;
  readonly administrationRoute: string | null;
  readonly scheduleText: string | null;
  readonly startsOn: string | null;
  readonly endsOn: string | null;
  readonly prescriberName: string | null;
  readonly authorizationReference: string | null;
  readonly instructions: string | null;
  readonly active: boolean;
  readonly createdAt: string;
}

export interface HealthVisit {
  readonly id: string;
  readonly incidentId: string | null;
  readonly visitedAt: string;
  readonly reason: string;
  readonly observations: string | null;
  readonly temperatureCelsius: number | null;
  readonly actionTaken: string | null;
  readonly returnToClass: boolean | null;
  readonly referredTo: string | null;
  readonly createdAt: string;
}

export interface HealthVaccination {
  readonly id: string;
  readonly vaccineName: string;
  readonly doseLabel: string | null;
  readonly administeredOn: string;
  readonly providerName: string | null;
  readonly documentReference: string | null;
  readonly nextDueOn: string | null;
  readonly status: 'RECORDED' | 'VERIFIED' | 'EXEMPTED';
  readonly createdAt: string;
}

export interface StudentHealthRecord {
  readonly safetyNotice: string;
  readonly student: {
    readonly id: string;
    readonly displayName: string;
    readonly birthDate: string;
    readonly classroomName: string | null;
  };
  readonly policy: HealthPolicy;
  readonly profile: HealthProfile;
  readonly allergies: readonly HealthAllergy[];
  readonly medications: readonly HealthMedication[];
  readonly incidents: readonly HealthIncident[];
  readonly visits: readonly HealthVisit[];
  readonly vaccinations: readonly HealthVaccination[];
}

@Injectable({ providedIn: 'root' })
export class HealthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/health';

  dashboard(): Observable<HealthDashboard> {
    return this.http.get<HealthDashboard>(`${this.baseUrl}/dashboard`);
  }

  student(studentId: string): Observable<StudentHealthRecord> {
    return this.http.get<StudentHealthRecord>(`${this.baseUrl}/students/${studentId}`);
  }

  saveProfile(
    studentId: string,
    command: Record<string, unknown>,
  ): Observable<StudentHealthRecord> {
    return this.http.put<StudentHealthRecord>(
      `${this.baseUrl}/students/${studentId}/profile`,
      command,
    );
  }

  addAllergy(studentId: string, command: Record<string, unknown>): Observable<HealthAllergy> {
    return this.http.post<HealthAllergy>(
      `${this.baseUrl}/students/${studentId}/allergies`,
      command,
    );
  }

  addMedication(studentId: string, command: Record<string, unknown>): Observable<HealthMedication> {
    return this.http.post<HealthMedication>(
      `${this.baseUrl}/students/${studentId}/medications`,
      command,
    );
  }

  addIncident(studentId: string, command: Record<string, unknown>): Observable<HealthIncident> {
    return this.http.post<HealthIncident>(
      `${this.baseUrl}/students/${studentId}/incidents`,
      command,
    );
  }

  addVisit(studentId: string, command: Record<string, unknown>): Observable<HealthVisit> {
    return this.http.post<HealthVisit>(`${this.baseUrl}/students/${studentId}/visits`, command);
  }

  addVaccination(
    studentId: string,
    command: Record<string, unknown>,
  ): Observable<HealthVaccination> {
    return this.http.post<HealthVaccination>(
      `${this.baseUrl}/students/${studentId}/vaccinations`,
      command,
    );
  }

  updatePolicy(vaccinationTrackingEnabled: boolean): Observable<HealthPolicy> {
    return this.http.put<HealthPolicy>(`${this.baseUrl}/policy`, {
      vaccinationTrackingEnabled,
    });
  }
}
