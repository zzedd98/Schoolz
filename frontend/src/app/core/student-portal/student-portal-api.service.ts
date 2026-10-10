import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface StudentPortalProfile {
  readonly studentId: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly preferredName: string | null;
  readonly studentNumber: string | null;
  readonly classroomId: string | null;
  readonly classroomCode: string | null;
  readonly classroomName: string | null;
  readonly gradeLevelName: string | null;
  readonly campusName: string | null;
  readonly academicYearName: string | null;
}

export interface StudentLesson {
  readonly id: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly roomName: string;
  readonly teacherName: string;
}

export interface StudentHomework {
  readonly id: string;
  readonly title: string;
  readonly instructions: string;
  readonly assignedOn: string;
  readonly dueAt: string;
  readonly estimatedMinutes: number | null;
  readonly status: 'TO_DO' | 'DONE';
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
}

export interface StudentAssessment {
  readonly id: string;
  readonly title: string;
  readonly description: string | null;
  readonly assessmentDate: string;
  readonly maximumScore: number | null;
  readonly coefficient: number;
  readonly typeName: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly teacherName: string;
}

export interface StudentGrade {
  readonly id: string;
  readonly title: string;
  readonly assessmentDate: string;
  readonly score: number | null;
  readonly maximumScore: number | null;
  readonly status: string;
  readonly teacherComment: string | null;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
}

export interface StudentCourse {
  readonly id: string;
  readonly lessonDate: string;
  readonly title: string;
  readonly summary: string | null;
  readonly content: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly teacherName: string;
  readonly attachmentCount: number;
}

export interface StudentDashboard {
  readonly requestedDate: string;
  readonly scheduleDate: string;
  readonly nextSchoolDate: string | null;
  readonly showingNextSchoolDay: boolean;
  readonly generatedAt: string;
  readonly student: StudentPortalProfile;
  readonly lessons: readonly StudentLesson[];
  readonly homework: readonly StudentHomework[];
  readonly assessments: readonly StudentAssessment[];
  readonly grades: readonly StudentGrade[];
  readonly courses: readonly StudentCourse[];
  readonly summary: {
    readonly averageOn20: number | null;
    readonly openHomeworkCount: number;
    readonly completedHomeworkCount: number;
    readonly absenceCount30Days: number;
  };
}

@Injectable({ providedIn: 'root' })
export class StudentPortalApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/student-portal';

  dashboard(date?: string): Observable<StudentDashboard> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<StudentDashboard>(`${this.base}/dashboard`, { params });
  }

  all(date?: string): Observable<StudentDashboard> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<StudentDashboard>(`${this.base}/all`, { params });
  }

  markHomework(homeworkId: string, status: 'TO_DO' | 'DONE'): Observable<StudentDashboard> {
    return this.http.put<StudentDashboard>(`${this.base}/homework/${homeworkId}/status`, {
      status,
    });
  }
}
