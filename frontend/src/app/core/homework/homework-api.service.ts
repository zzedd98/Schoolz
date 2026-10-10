import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface HomeworkTermOption {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly startsOn: string;
  readonly endsOn: string;
}

export interface HomeworkClassroomOption {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly gradeLevelName: string;
  readonly schoolLevelName: string;
}

export interface TeacherAssignmentOption {
  readonly id: string;
  readonly academicYearId: string;
  readonly classroomId: string;
  readonly classroomCode: string;
  readonly classroomName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
}

export interface HomeworkReferences {
  readonly terms: readonly HomeworkTermOption[];
  readonly classrooms: readonly HomeworkClassroomOption[];
  readonly assignments: readonly TeacherAssignmentOption[];
}

export interface HomeworkAttachment {
  readonly id: string;
  readonly fileName: string;
  readonly contentType: string;
  readonly sizeBytes: number;
  readonly sha256: string;
  readonly createdAt: string;
}

export interface LessonContentView {
  readonly id: string;
  readonly teacherAssignmentId: string;
  readonly classroomId: string;
  readonly classroomName: string;
  readonly academicTermId: string;
  readonly termName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly lessonDate: string;
  readonly title: string;
  readonly summary: string | null;
  readonly content: string;
  readonly status: 'DRAFT' | 'PUBLISHED';
  readonly publishedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
  readonly attachments: readonly HomeworkAttachment[];
}

export interface HomeworkView {
  readonly id: string;
  readonly teacherAssignmentId: string;
  readonly lessonContentId: string | null;
  readonly classroomId: string;
  readonly classroomName: string;
  readonly academicTermId: string;
  readonly termName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly title: string;
  readonly instructions: string;
  readonly assignedOn: string;
  readonly dueAt: string;
  readonly estimatedMinutes: number | null;
  readonly status: 'DRAFT' | 'PUBLISHED' | 'CLOSED';
  readonly publishedAt: string | null;
  readonly closedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
  readonly totalStudents: number;
  readonly completedStudents: number;
  readonly overdue: boolean;
  readonly attachments: readonly HomeworkAttachment[];
}

export interface HomeworkDashboard {
  readonly classroomId: string;
  readonly classroomName: string;
  readonly academicTermId: string;
  readonly termName: string;
  readonly from: string;
  readonly to: string;
  readonly studentCount: number;
  readonly lessonContents: readonly LessonContentView[];
  readonly homework: readonly HomeworkView[];
  readonly publishedHomeworkCount: number;
}

export interface LessonContentCommand {
  readonly teacherAssignmentId: string;
  readonly academicTermId: string;
  readonly lessonDate: string;
  readonly title: string;
  readonly summary?: string;
  readonly content: string;
}

export interface HomeworkCommand {
  readonly teacherAssignmentId: string;
  readonly academicTermId: string;
  readonly lessonContentId?: string | null;
  readonly title: string;
  readonly instructions: string;
  readonly assignedOn: string;
  readonly dueAt: string;
  readonly estimatedMinutes?: number | null;
}

export interface HomeworkChild {
  readonly id: string;
  readonly name: string;
  readonly classroomId: string;
  readonly classroomName: string;
}

export interface ParentLessonContent {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly classroomName: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly lessonDate: string;
  readonly title: string;
  readonly summary: string | null;
  readonly content: string;
  readonly attachments: readonly HomeworkAttachment[];
}

export interface ParentHomework {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly classroomName: string;
  readonly subjectCode: string;
  readonly subjectNameFr: string;
  readonly subjectNameAr: string | null;
  readonly color: string;
  readonly title: string;
  readonly instructions: string;
  readonly assignedOn: string;
  readonly dueAt: string;
  readonly estimatedMinutes: number | null;
  readonly publicationStatus: 'PUBLISHED' | 'CLOSED';
  readonly studentStatus: 'TO_DO' | 'DONE';
  readonly completedAt: string | null;
  readonly overdue: boolean;
  readonly attachments: readonly HomeworkAttachment[];
}

export interface ParentHomeworkDashboard {
  readonly generatedAt: string;
  readonly from: string;
  readonly to: string;
  readonly children: readonly HomeworkChild[];
  readonly lessonContents: readonly ParentLessonContent[];
  readonly homework: readonly ParentHomework[];
}

@Injectable({ providedIn: 'root' })
export class HomeworkApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/homework';
  private readonly portal = '/api/v1/public/homework';

  references(): Observable<HomeworkReferences> {
    return this.http.get<HomeworkReferences>(`${this.base}/references`);
  }

  dashboard(
    classroomId: string,
    academicTermId: string,
    from?: string,
    to?: string,
  ): Observable<HomeworkDashboard> {
    let params = new HttpParams()
      .set('classroomId', classroomId)
      .set('academicTermId', academicTermId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<HomeworkDashboard>(`${this.base}/dashboard`, { params });
  }

  createLesson(command: LessonContentCommand): Observable<LessonContentView> {
    return this.http.post<LessonContentView>(`${this.base}/lesson-contents`, command);
  }

  publishLesson(id: string): Observable<LessonContentView> {
    return this.http.post<LessonContentView>(`${this.base}/lesson-contents/${id}/publish`, {});
  }

  createHomework(command: HomeworkCommand): Observable<HomeworkView> {
    return this.http.post<HomeworkView>(`${this.base}/assignments`, command);
  }

  publishHomework(id: string): Observable<HomeworkView> {
    return this.http.post<HomeworkView>(`${this.base}/assignments/${id}/publish`, {});
  }

  closeHomework(id: string): Observable<HomeworkView> {
    return this.http.post<HomeworkView>(`${this.base}/assignments/${id}/close`, {});
  }

  uploadAttachment(
    owner: 'lesson-contents' | 'assignments',
    id: string,
    file: File,
  ): Observable<HomeworkAttachment> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<HomeworkAttachment>(`${this.base}/${owner}/${id}/attachments`, body);
  }

  downloadAttachment(id: string, parent = false): Observable<Blob> {
    const url = parent ? `${this.portal}/attachments/${id}` : `${this.base}/attachments/${id}`;
    return this.http.get(url, { responseType: 'blob', withCredentials: parent });
  }

  parentDashboard(
    from: string,
    to: string,
    studentId?: string,
  ): Observable<ParentHomeworkDashboard> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (studentId) params = params.set('studentId', studentId);
    return this.http.get<ParentHomeworkDashboard>(this.portal, {
      params,
      withCredentials: true,
    });
  }

  markStatus(
    homeworkId: string,
    studentId: string,
    status: 'TO_DO' | 'DONE',
  ): Observable<ParentHomework> {
    return this.http.patch<ParentHomework>(
      `${this.portal}/assignments/${homeworkId}/status`,
      { studentId, status },
      { withCredentials: true },
    );
  }
}
