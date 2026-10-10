import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ChildcareEventType =
  | 'MEAL'
  | 'BOTTLE'
  | 'SLEEP'
  | 'DIAPER'
  | 'TOILET'
  | 'ACTIVITY'
  | 'MOOD'
  | 'OBSERVATION'
  | 'INCIDENT'
  | 'TEMPERATURE'
  | 'MEDICATION';

export interface ChildcareGroup {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly campusId: string;
  readonly minAgeMonths: number;
  readonly maxAgeMonths: number;
  readonly capacity: number;
  readonly referenceEducator: string | null;
  readonly childCount: number;
}

export interface PickupPerson {
  readonly id: string;
  readonly displayName: string;
  readonly relationshipType: string;
  readonly phone: string | null;
  readonly authorizationStatus: 'AUTHORIZED' | 'PROHIBITED';
  readonly identityReference: string | null;
  readonly notes: string | null;
}

export interface ChildcareEvent {
  readonly id: string;
  readonly eventType: ChildcareEventType;
  readonly occurredAt: string;
  readonly endedAt: string | null;
  readonly label: string | null;
  readonly quantity: number | null;
  readonly unit: string | null;
  readonly outcome: string | null;
  readonly notes: string | null;
  readonly temperatureCelsius: number | null;
  readonly incidentSeverity: string | null;
  readonly medicationName: string | null;
  readonly medicationDose: string | null;
}

export interface ChildcareDailyLog {
  readonly id: string | null;
  readonly date: string;
  readonly arrivalAt: string | null;
  readonly departureAt: string | null;
  readonly pickupPerson: string | null;
  readonly mood: string | null;
  readonly summary: string | null;
  readonly status: 'NOT_STARTED' | 'DRAFT' | 'PUBLISHED';
  readonly publishedAt: string | null;
  readonly events: readonly ChildcareEvent[];
}

export interface ChildcareChild {
  readonly profileId: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly birthDate: string;
  readonly groupId: string;
  readonly groupName: string;
  readonly expectedArrivalTime: string;
  readonly expectedDepartureTime: string;
  readonly mealInstructions: string | null;
  readonly bottleInstructions: string | null;
  readonly sleepInstructions: string | null;
  readonly toiletStage: string | null;
  readonly pickupPeople: readonly PickupPerson[];
  readonly dailyLog: ChildcareDailyLog;
}

export interface ChildcareDashboard {
  readonly date: string;
  readonly groups: readonly ChildcareGroup[];
  readonly children: readonly ChildcareChild[];
  readonly policy: {
    readonly medicationRecordingEnabled: boolean;
    readonly medicationAuthorizationRequired: boolean;
    readonly instructions: string | null;
  };
}

export interface ChildcareDailyReport {
  readonly studentId: string;
  readonly studentName: string;
  readonly groupName: string;
  readonly dailyLog: ChildcareDailyLog;
}

export interface EventCommand {
  readonly eventType: ChildcareEventType;
  readonly occurredAt?: string;
  readonly endedAt?: string;
  readonly label?: string;
  readonly quantity?: number;
  readonly unit?: string;
  readonly outcome?: string;
  readonly notes?: string;
  readonly temperatureCelsius?: number;
  readonly incidentSeverity?: string;
  readonly medicationName?: string;
  readonly medicationDose?: string;
  readonly authorizationReference?: string;
}

@Injectable({ providedIn: 'root' })
export class ChildcareApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/childcare';

  dashboard(date: string, groupId?: string): Observable<ChildcareDashboard> {
    const params: Record<string, string> = { date };
    if (groupId) params['groupId'] = groupId;
    return this.http.get<ChildcareDashboard>(`${this.base}/dashboard`, { params });
  }

  arrival(studentId: string): Observable<ChildcareChild> {
    return this.http.post<ChildcareChild>(`${this.base}/children/${studentId}/arrival`, {});
  }

  departure(studentId: string, pickupPersonId: string): Observable<ChildcareChild> {
    return this.http.post<ChildcareChild>(`${this.base}/children/${studentId}/departure`, {
      pickupPersonId,
    });
  }

  addEvent(studentId: string, command: EventCommand): Observable<ChildcareChild> {
    return this.http.post<ChildcareChild>(`${this.base}/children/${studentId}/events`, command);
  }

  publish(studentId: string, date: string, summary: string): Observable<ChildcareChild> {
    return this.http.post<ChildcareChild>(`${this.base}/children/${studentId}/publish`, {
      date,
      summary,
    });
  }

  reports(date: string): Observable<readonly ChildcareDailyReport[]> {
    return this.http.get<readonly ChildcareDailyReport[]>('/api/v1/public/childcare/reports', {
      params: { date },
      withCredentials: true,
    });
  }
}
