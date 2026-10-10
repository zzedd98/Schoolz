import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FamilyParentAccount {
  readonly id: string;
  readonly displayName: string;
  readonly email: string;
  readonly locale: string;
}

export interface FamilyChild {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly fullName: string;
  readonly birthDate: string | null;
  readonly studentNumber: string | null;
  readonly classroomId: string | null;
  readonly classroomName: string | null;
  readonly gradeLevelName: string | null;
  readonly campusName: string | null;
  readonly academicYearName: string | null;
  readonly enrollmentStatus: string | null;
  readonly childcare: boolean;
}

export interface FamilyGrade {
  readonly id: string;
  readonly assessmentId: string;
  readonly title: string;
  readonly assessmentDate: string;
  readonly score: number;
  readonly maximumScore: number;
  readonly status: string;
  readonly teacherComment: string | null;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
}

export interface FamilyHomework {
  readonly id: string;
  readonly title: string;
  readonly instructions: string;
  readonly dueAt: string;
  readonly estimatedMinutes: number | null;
  readonly status: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
}

export interface FamilyTimetableLesson {
  readonly id: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly roomName: string | null;
  readonly teacherName: string | null;
}

export interface FamilyReportCard {
  readonly id: string;
  readonly termName: string;
  readonly academicYearName: string;
  readonly overallAverage: number | null;
  readonly classRank: number | null;
  readonly classSize: number | null;
  readonly versionNumber: number;
  readonly languageMode: string;
  readonly fileName: string;
  readonly sha256: string;
  readonly publishedAt: string;
}

export interface FamilyAbsence {
  readonly id: string;
  readonly date: string;
  readonly resolutionStatus: string;
  readonly subjectName: string | null;
  readonly reasonFr: string | null;
  readonly reasonAr: string | null;
  readonly justificationId: string | null;
  readonly justificationStatus: string | null;
}

export interface FamilyModule {
  readonly code:
    | 'SCHOOLING'
    | 'TIMETABLE'
    | 'HOMEWORK'
    | 'GRADES'
    | 'REPORT_CARDS'
    | 'ATTENDANCE'
    | 'JUSTIFICATION'
    | 'CHILDCARE'
    | 'FINANCE'
    | 'MESSAGES'
    | 'DOCUMENTS'
    | 'TRANSPORT'
    | 'ACTIVITIES'
    | 'LIBRARY';
  readonly available: boolean;
  readonly targetPath: string | null;
  readonly availableFromMilestone: number | null;
}

export interface FamilyDashboard {
  readonly generatedAt: string;
  readonly date: string;
  readonly account: FamilyParentAccount;
  readonly children: readonly FamilyChild[];
  readonly selectedStudentId: string | null;
  readonly overview: {
    readonly attendanceToday: {
      readonly status: string;
      readonly sessionCount: number;
      readonly exceptionSubject: string | null;
    };
    readonly latestGrade: FamilyGrade | null;
    readonly nextHomework: FamilyHomework | null;
    readonly nextSchoolDate: string | null;
    readonly openHomeworkCount: number;
    readonly absenceCount30Days: number;
    readonly unexcusedAbsenceCount: number;
    readonly unreadNotificationCount: number;
  } | null;
  readonly timetable: readonly FamilyTimetableLesson[];
  readonly grades: readonly FamilyGrade[];
  readonly reportCards: readonly FamilyReportCard[];
  readonly absences: readonly FamilyAbsence[];
  readonly modules: readonly FamilyModule[];
}

@Injectable({ providedIn: 'root' })
export class FamilyPortalApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/public/family-portal';
  private readonly options = { withCredentials: true } as const;

  dashboard(studentId?: string, date?: string): Observable<FamilyDashboard> {
    let params = new HttpParams();
    if (studentId) params = params.set('studentId', studentId);
    if (date) params = params.set('date', date);
    return this.http.get<FamilyDashboard>(this.base, { ...this.options, params });
  }

  prefer(studentId: string): Observable<FamilyDashboard> {
    return this.http.put<FamilyDashboard>(`${this.base}/preference`, { studentId }, this.options);
  }

  reportCard(studentId: string, reportCardId: string): Observable<Blob> {
    return this.http.get(`${this.base}/children/${studentId}/report-cards/${reportCardId}/pdf`, {
      ...this.options,
      responseType: 'blob',
    });
  }
}
