import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type SchoolEventType =
  'HOLIDAY' | 'EXAM' | 'MEETING' | 'TRIP' | 'ACTIVITY' | 'PARENT_TEACHER';

export interface SchoolCalendarEvent {
  readonly id: string;
  readonly title: string;
  readonly description: string | null;
  readonly eventType: SchoolEventType;
  readonly audienceScope: 'SCHOOL' | 'CAMPUS' | 'CLASS';
  readonly campusId: string | null;
  readonly campusName: string | null;
  readonly classroomId: string | null;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly allDay: boolean;
  readonly location: string | null;
  readonly status: 'SCHEDULED' | 'CANCELLED';
  readonly syncStatus: 'LOCAL_ONLY' | 'SYNCED' | 'PENDING' | 'ERROR';
}

export interface SchoolCalendarView {
  readonly generatedAt: string;
  readonly from: string;
  readonly to: string;
  readonly eventTypes: readonly SchoolEventType[];
  readonly counts: Readonly<Partial<Record<SchoolEventType, number>>>;
  readonly campuses: readonly { readonly id: string; readonly name: string }[];
  readonly events: readonly SchoolCalendarEvent[];
}

export interface SchoolCalendarCommand {
  title: string;
  description: string;
  eventType: SchoolEventType;
  audienceScope: 'SCHOOL' | 'CAMPUS';
  campusId?: string;
  startsAt: string;
  endsAt: string;
  allDay: boolean;
  location: string;
}

@Injectable({ providedIn: 'root' })
export class SchoolCalendarApiService {
  private readonly http = inject(HttpClient);

  calendar(from: string, to: string, type = '', campusId = '') {
    let params = new HttpParams().set('from', from).set('to', to);
    if (type) params = params.set('type', type);
    if (campusId) params = params.set('campusId', campusId);
    return this.http.get<SchoolCalendarView>('/api/v1/calendar', { params });
  }

  create(command: SchoolCalendarCommand) {
    return this.http.post<SchoolCalendarEvent>('/api/v1/calendar', command);
  }

  cancel(eventId: string) {
    return this.http.put<SchoolCalendarEvent>(`/api/v1/calendar/${eventId}/cancel`, {});
  }
}
