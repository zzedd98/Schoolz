import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TeacherPortalProfile {
  readonly staffProfileId: string;
  readonly employeeNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly jobTitle: string;
}

export interface TeacherPortalLesson {
  readonly lessonId: string;
  readonly assignmentId: string;
  readonly classroomId: string;
  readonly classroomCode: string;
  readonly classroomName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly roomId: string;
  readonly roomName: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly timelineStatus: 'COMPLETED' | 'IN_PROGRESS' | 'NEXT' | 'UPCOMING';
  readonly attendanceSessionId: string | null;
  readonly attendanceStatus: 'OPEN' | 'SUBMITTED' | 'CANCELLED' | null;
}

export interface TeacherPortalAction {
  readonly code: 'TAKE_ATTENDANCE' | 'ENTER_GRADES' | 'ADD_HOMEWORK' | 'OBSERVATION' | 'MESSAGE';
  readonly enabled: boolean;
  readonly targetPath: string | null;
  readonly unavailableReason: string | null;
}

export interface TeacherPortalDashboard {
  readonly date: string;
  readonly generatedAt: string;
  readonly teacher: TeacherPortalProfile;
  readonly lessons: readonly TeacherPortalLesson[];
  readonly summary: {
    readonly lessonCount: number;
    readonly completedCount: number;
    readonly attendanceSubmittedCount: number;
  };
  readonly nextTeachingDate: string | null;
  readonly quickActions: readonly TeacherPortalAction[];
}

@Injectable({ providedIn: 'root' })
export class TeacherPortalApiService {
  private readonly http = inject(HttpClient);

  today(date?: string): Observable<TeacherPortalDashboard> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<TeacherPortalDashboard>('/api/v1/teacher-portal/today', { params });
  }
}
