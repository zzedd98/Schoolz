import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_DEPARTURE';

export interface AbsenceReason {
  readonly id: string;
  readonly code: string;
  readonly nameFr: string;
  readonly nameAr: string;
  readonly category: string;
  readonly requiresDocument: boolean;
}

export interface AttendanceReferences {
  readonly classrooms: readonly {
    readonly id: string;
    readonly code: string;
    readonly name: string;
    readonly campusId: string;
    readonly academicYearId: string;
  }[];
  readonly teachers: readonly {
    readonly id: string;
    readonly employeeNumber: string;
    readonly name: string;
  }[];
  readonly absenceReasons: readonly AbsenceReason[];
}

export interface AttendanceSessionSummary {
  readonly id: string;
  readonly date: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly status: 'OPEN' | 'SUBMITTED' | 'CANCELLED';
  readonly classroomName: string;
  readonly teacherName: string;
  readonly totalCount: number;
  readonly presentCount: number;
  readonly absentCount: number;
  readonly lateCount: number;
}

export interface AttendanceRecord {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly status: AttendanceStatus;
  readonly notes: string | null;
  readonly arrivedAt: string | null;
  readonly minutesLate: number | null;
  readonly leftAt: string | null;
  readonly departureReason: string | null;
  readonly pickupPersonName: string | null;
  readonly absenceId: string | null;
  readonly absenceResolution: string | null;
  readonly justificationId: string | null;
  readonly justificationStatus: string | null;
}

export interface AttendanceSession {
  readonly id: string;
  readonly date: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly status: 'OPEN' | 'SUBMITTED' | 'CANCELLED';
  readonly submittedAt: string | null;
  readonly classroomId: string;
  readonly classroomName: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly records: readonly AttendanceRecord[];
}

export interface AttendanceJustification {
  readonly id: string;
  readonly absenceId: string;
  readonly studentName: string;
  readonly absenceDate: string;
  readonly subjectName: string;
  readonly classroomName: string;
  readonly reasonId: string;
  readonly reasonName: string;
  readonly parentStatement: string;
  readonly evidenceObjectKey: string | null;
  readonly submittedAt: string;
  readonly reviewStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  readonly reviewedAt: string | null;
  readonly reviewNotes: string | null;
}

export interface AttendanceStatistics {
  readonly from: string;
  readonly to: string;
  readonly totalRecords: number;
  readonly presentCount: number;
  readonly absentCount: number;
  readonly lateCount: number;
  readonly earlyDepartureCount: number;
  readonly unexcusedCount: number;
  readonly absenceRate: number;
  readonly trend: readonly {
    readonly date: string;
    readonly absentCount: number;
    readonly lateCount: number;
    readonly totalCount: number;
  }[];
}

export interface ParentAbsence {
  readonly absenceId: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly date: string;
  readonly subjectName: string;
  readonly classroomName: string;
  readonly resolutionStatus: 'UNEXCUSED' | 'PENDING' | 'EXCUSED' | 'REJECTED';
  readonly reasonName: string | null;
  readonly justificationId: string | null;
  readonly justificationStatus: string | null;
  readonly parentStatement: string | null;
}

export interface AttendanceNotification {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly absenceId: string | null;
  readonly notificationType: string;
  readonly title: string;
  readonly message: string;
  readonly status: 'UNREAD' | 'READ';
  readonly createdAt: string;
  readonly readAt: string | null;
}

export interface ParentAttendanceDashboard {
  readonly from: string;
  readonly to: string;
  readonly absences: readonly ParentAbsence[];
  readonly absenceReasons: readonly AbsenceReason[];
  readonly notifications: readonly AttendanceNotification[];
}

export interface RecordCommand {
  readonly recordId: string;
  readonly status: AttendanceStatus;
  readonly notes?: string;
  readonly arrivedAt?: string;
  readonly leftAt?: string;
  readonly departureReason?: string;
  readonly pickupPersonName?: string;
}

@Injectable({ providedIn: 'root' })
export class AttendanceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/attendance';

  references(): Observable<AttendanceReferences> {
    return this.http.get<AttendanceReferences>(`${this.base}/references`);
  }

  sessions(date: string): Observable<readonly AttendanceSessionSummary[]> {
    return this.http.get<readonly AttendanceSessionSummary[]>(`${this.base}/sessions`, {
      params: { date },
    });
  }

  session(sessionId: string): Observable<AttendanceSession> {
    return this.http.get<AttendanceSession>(`${this.base}/sessions/${sessionId}`);
  }

  createSession(command: {
    classroomId: string;
    teacherStaffId: string;
    sessionDate: string;
    subjectCode: string;
    subjectName: string;
    startsAt: string;
    endsAt: string;
  }): Observable<AttendanceSession> {
    return this.http.post<AttendanceSession>(`${this.base}/sessions`, command);
  }

  updateRecords(
    sessionId: string,
    records: readonly RecordCommand[],
  ): Observable<AttendanceSession> {
    return this.http.put<AttendanceSession>(`${this.base}/sessions/${sessionId}/records`, {
      records,
    });
  }

  submit(sessionId: string): Observable<AttendanceSession> {
    return this.http.post<AttendanceSession>(`${this.base}/sessions/${sessionId}/submit`, {});
  }

  justifications(status = 'PENDING'): Observable<readonly AttendanceJustification[]> {
    return this.http.get<readonly AttendanceJustification[]>(`${this.base}/justifications`, {
      params: { status },
    });
  }

  reviewJustification(
    justificationId: string,
    decision: 'APPROVED' | 'REJECTED',
    notes: string,
  ): Observable<AttendanceJustification> {
    return this.http.patch<AttendanceJustification>(
      `${this.base}/justifications/${justificationId}`,
      { decision, notes },
    );
  }

  statistics(from: string, to: string, classroomId?: string): Observable<AttendanceStatistics> {
    const params: Record<string, string> = { from, to };
    if (classroomId) params['classroomId'] = classroomId;
    return this.http.get<AttendanceStatistics>(`${this.base}/statistics`, { params });
  }

  createIncident(command: {
    studentId: string;
    classroomId?: string;
    category: string;
    severity: string;
    description: string;
  }): Observable<{ readonly id: string }> {
    return this.http.post<{ readonly id: string }>(`${this.base}/incidents`, command);
  }

  createSanction(
    incidentId: string,
    command: { sanctionType: string; description: string; startsOn: string },
  ): Observable<unknown> {
    return this.http.post(`${this.base}/incidents/${incidentId}/sanctions`, command);
  }

  createObservation(command: {
    studentId: string;
    classroomId?: string;
    teacherStaffId: string;
    observationDate: string;
    category: string;
    observation: string;
    visibleToParent: boolean;
  }): Observable<unknown> {
    return this.http.post(`${this.base}/observations`, command);
  }

  parentDashboard(from: string, to: string): Observable<ParentAttendanceDashboard> {
    return this.http.get<ParentAttendanceDashboard>('/api/v1/public/attendance', {
      params: { from, to },
      withCredentials: true,
    });
  }

  justify(
    absenceId: string,
    command: { reasonId: string; statement: string; evidenceObjectKey?: string },
  ): Observable<ParentAbsence> {
    return this.http.post<ParentAbsence>(
      `/api/v1/public/attendance/absences/${absenceId}/justifications`,
      command,
      { withCredentials: true },
    );
  }

  readNotification(notificationId: string): Observable<void> {
    return this.http.patch<void>(
      `/api/v1/public/attendance/notifications/${notificationId}/read`,
      {},
      { withCredentials: true },
    );
  }
}
