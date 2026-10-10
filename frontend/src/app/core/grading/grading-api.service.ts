import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface GradingReferences {
  readonly years: readonly YearOption[];
  readonly terms: readonly TermOption[];
  readonly schoolLevels: readonly SchoolLevelOption[];
  readonly gradeLevels: readonly GradeLevelOption[];
  readonly classrooms: readonly ClassroomOption[];
  readonly teachers: readonly TeacherOption[];
  readonly subjects: readonly SubjectView[];
  readonly scales: readonly GradeScaleView[];
  readonly policies: readonly PolicyView[];
  readonly assessmentTypes: readonly AssessmentTypeView[];
  readonly coefficients: readonly CoefficientView[];
  readonly competencies: readonly CompetencyView[];
  readonly templates: readonly ReportCardTemplateView[];
}

export interface YearOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly status: string;
  readonly startsOn: string;
  readonly endsOn: string;
}

export interface TermOption {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly sequenceNumber: number;
  readonly startsOn: string;
  readonly endsOn: string;
}

export interface SchoolLevelOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly sequenceNumber: number;
}

export interface GradeLevelOption {
  readonly id: string;
  readonly schoolLevelId: string;
  readonly code: string;
  readonly name: string;
  readonly sequenceNumber: number;
}

export interface ClassroomOption {
  readonly id: string;
  readonly academicYearId: string;
  readonly gradeLevelId: string;
  readonly schoolLevelId: string;
  readonly schoolLevelCode: string;
  readonly schoolLevelName: string;
  readonly code: string;
  readonly name: string;
}

export interface TeacherOption {
  readonly id: string;
  readonly employeeNumber: string;
  readonly name: string;
}

export interface SubjectView {
  readonly id: string;
  readonly code: string;
  readonly nameFr: string;
  readonly nameAr: string | null;
  readonly color: string;
}

export interface CompetencyLevel {
  readonly code: string;
  readonly labelFr: string;
  readonly labelAr: string | null;
  readonly sequenceNumber: number;
  readonly color: string;
}

export interface GradeScaleView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly scaleType: 'NUMERIC' | 'COMPETENCY';
  readonly minimumScore: number | null;
  readonly maximumScore: number | null;
  readonly passingScore: number | null;
  readonly roundingScale: number;
  readonly competencyLevels: readonly CompetencyLevel[];
  readonly active: boolean;
}

export type EvaluationMode = 'NONE' | 'COMPETENCY_ONLY' | 'HYBRID' | 'NUMERIC';

export interface PolicyView {
  readonly id: string;
  readonly scopeType: 'SCHOOL_LEVEL' | 'CHILDCARE';
  readonly scopeKey: string;
  readonly schoolLevelId: string | null;
  readonly schoolLevelCode: string | null;
  readonly schoolLevelName: string | null;
  readonly code: string;
  readonly name: string;
  readonly evaluationMode: EvaluationMode;
  readonly gradeScaleId: string | null;
  readonly competencyScaleId: string | null;
  readonly calculationStrategy: 'WEIGHTED_MEAN' | 'CATEGORY_WEIGHTED';
  readonly calculationConfig: Readonly<Record<string, unknown>>;
  readonly rankingsEnabled: boolean;
  readonly reportCardsEnabled: boolean;
  readonly active: boolean;
}

export interface AssessmentTypeView {
  readonly id: string;
  readonly code: string;
  readonly nameFr: string;
  readonly nameAr: string | null;
  readonly calculationCategory: string;
  readonly numericEnabled: boolean;
  readonly competencyEnabled: boolean;
  readonly active: boolean;
}

export interface CoefficientView {
  readonly id: string;
  readonly academicYearId: string;
  readonly gradeLevelId: string;
  readonly gradeLevelCode: string;
  readonly gradeLevelName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly coefficient: number;
}

export interface CompetencyView {
  readonly id: string;
  readonly schoolLevelId: string;
  readonly schoolLevelCode: string;
  readonly schoolLevelName: string;
  readonly subjectId: string | null;
  readonly subjectCode: string | null;
  readonly subjectName: string | null;
  readonly parentCompetencyId: string | null;
  readonly code: string;
  readonly nameFr: string;
  readonly nameAr: string | null;
  readonly description: string | null;
  readonly sequenceNumber: number;
  readonly active: boolean;
}

export interface ReportCardTemplateView {
  readonly id: string;
  readonly schoolLevelId: string;
  readonly schoolLevelCode: string;
  readonly schoolLevelName: string;
  readonly code: string;
  readonly name: string;
  readonly languageMode: 'FR' | 'AR' | 'BILINGUAL';
  readonly layoutConfig: Readonly<Record<string, unknown>>;
  readonly versionNumber: number;
  readonly active: boolean;
}

export interface AssessmentView {
  readonly id: string;
  readonly academicYearId: string;
  readonly academicTermId: string;
  readonly classroomId: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly color: string;
  readonly assessmentTypeId: string;
  readonly assessmentTypeCode: string;
  readonly assessmentTypeName: string;
  readonly calculationCategory: string;
  readonly numericEnabled: boolean;
  readonly competencyEnabled: boolean;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly title: string;
  readonly description: string | null;
  readonly assessmentDate: string;
  readonly maximumScore: number | null;
  readonly coefficient: number;
  readonly status: 'DRAFT' | 'PUBLISHED';
  readonly gradeCount: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface GradeView {
  readonly id: string;
  readonly assessmentId: string;
  readonly assessmentTitle: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly color: string;
  readonly rawScore: number | null;
  readonly maximumScore: number;
  readonly status: 'GRADED' | 'ABSENT' | 'EXCUSED' | 'NOT_SUBMITTED';
  readonly teacherComment: string | null;
  readonly version: number;
  readonly assessmentCoefficient: number;
  readonly calculationCategory: string;
  readonly updatedAt: string;
}

export interface GradeHistoryView {
  readonly id: string;
  readonly changeType: 'CREATED' | 'UPDATED';
  readonly previousScore: number | null;
  readonly newScore: number | null;
  readonly previousStatus: string | null;
  readonly newStatus: string;
  readonly reason: string | null;
  readonly changedAt: string;
  readonly actorUserId: string;
  readonly actorName: string;
}

export interface CompetencyEvaluationView {
  readonly id: string;
  readonly competencyId: string;
  readonly competencyCode: string;
  readonly competencyName: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly academicTermId: string;
  readonly assessmentId: string | null;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly levelCode: string;
  readonly observation: string | null;
  readonly evaluatedAt: string;
}

export interface TeacherCommentView {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly academicTermId: string;
  readonly subjectId: string | null;
  readonly subjectCode: string | null;
  readonly subjectName: string | null;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly commentType: string;
  readonly body: string;
  readonly visibleToFamily: boolean;
  readonly createdAt: string;
}

export interface StudentView {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly displayName: string;
  readonly identifier: string | null;
  readonly childcare: boolean;
}

export interface SubjectSummary {
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly color: string;
  readonly average: number | null;
  readonly coefficient: number;
  readonly categoryAverages: Readonly<Record<string, number>>;
  readonly includedGrades: number;
}

export interface StudentSummary {
  readonly studentId: string;
  readonly studentName: string;
  readonly subjects: readonly SubjectSummary[];
  readonly overallAverage: number | null;
  readonly rank: number | null;
  readonly classSize: number | null;
}

export interface ReportCardView {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly academicYearId: string;
  readonly academicTermId: string;
  readonly classroomId: string;
  readonly policyId: string;
  readonly templateId: string | null;
  readonly templateName: string | null;
  readonly status: string;
  readonly overallAverage: number | null;
  readonly classRank: number | null;
  readonly classSize: number | null;
  readonly versionNumber: number;
  readonly subjectCount: number;
  readonly calculatedAt: string;
  readonly supersedesReportCardId: string | null;
  readonly languageMode: 'FR' | 'AR' | 'BILINGUAL' | null;
  readonly fileName: string | null;
  readonly pdfSha256: string | null;
  readonly pdfSize: number | null;
  readonly signedByName: string | null;
  readonly signedByTitle: string | null;
  readonly publishedAt: string | null;
}

export interface ReportCardPublicationCommand {
  readonly languageMode: 'FR' | 'AR' | 'BILINGUAL';
  readonly signerName: string;
  readonly signerTitle?: string;
  readonly publicationNote?: string;
}

export interface ReportCardPublicationView {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly academicTermId: string;
  readonly status: 'CALCULATED' | 'PUBLISHED';
  readonly versionNumber: number;
  readonly supersedesReportCardId: string | null;
  readonly languageMode: 'FR' | 'AR' | 'BILINGUAL' | null;
  readonly fileName: string | null;
  readonly pdfSha256: string | null;
  readonly pdfSize: number | null;
  readonly signedByName: string | null;
  readonly signedByTitle: string | null;
  readonly publicationNote: string | null;
  readonly publishedAt: string | null;
}

export interface GradingDashboard {
  readonly context: {
    readonly classroomId: string;
    readonly classroomName: string;
    readonly academicYearId: string;
    readonly academicYearName: string;
    readonly academicTermId: string;
    readonly academicTermName: string;
    readonly gradeLevelId: string;
    readonly gradeLevelName: string;
    readonly schoolLevelId: string;
    readonly schoolLevelCode: string;
    readonly schoolLevelName: string;
  };
  readonly policy: PolicyView;
  readonly students: readonly StudentView[];
  readonly assessments: readonly AssessmentView[];
  readonly grades: readonly GradeView[];
  readonly competencies: readonly CompetencyView[];
  readonly competencyEvaluations: readonly CompetencyEvaluationView[];
  readonly teacherComments: readonly TeacherCommentView[];
  readonly summaries: readonly StudentSummary[];
  readonly reportCards: readonly ReportCardView[];
}

@Injectable({ providedIn: 'root' })
export class GradingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/grading';

  references(): Observable<GradingReferences> {
    return this.http.get<GradingReferences>(`${this.base}/references`);
  }

  dashboard(classroomId: string, academicTermId: string): Observable<GradingDashboard> {
    return this.http.get<GradingDashboard>(`${this.base}/dashboard`, {
      params: { classroomId, academicTermId },
    });
  }

  createScale(command: Readonly<Record<string, unknown>>): Observable<GradeScaleView> {
    return this.http.post<GradeScaleView>(`${this.base}/scales`, command);
  }

  savePolicy(command: Readonly<Record<string, unknown>>): Observable<PolicyView> {
    return this.http.put<PolicyView>(`${this.base}/policies`, command);
  }

  createAssessmentType(command: Readonly<Record<string, unknown>>): Observable<AssessmentTypeView> {
    return this.http.post<AssessmentTypeView>(`${this.base}/assessment-types`, command);
  }

  saveCoefficient(command: Readonly<Record<string, unknown>>): Observable<CoefficientView> {
    return this.http.put<CoefficientView>(`${this.base}/coefficients`, command);
  }

  createCompetency(command: Readonly<Record<string, unknown>>): Observable<CompetencyView> {
    return this.http.post<CompetencyView>(`${this.base}/competencies`, command);
  }

  createTemplate(command: Readonly<Record<string, unknown>>): Observable<ReportCardTemplateView> {
    return this.http.post<ReportCardTemplateView>(`${this.base}/report-card-templates`, command);
  }

  createAssessment(command: Readonly<Record<string, unknown>>): Observable<AssessmentView> {
    return this.http.post<AssessmentView>(`${this.base}/assessments`, command);
  }

  publishAssessment(assessmentId: string): Observable<AssessmentView> {
    return this.http.post<AssessmentView>(`${this.base}/assessments/${assessmentId}/publish`, {});
  }

  saveGrade(
    assessmentId: string,
    studentId: string,
    command: Readonly<Record<string, unknown>>,
  ): Observable<GradeView> {
    return this.http.put<GradeView>(
      `${this.base}/assessments/${assessmentId}/grades/${studentId}`,
      command,
    );
  }

  gradeHistory(gradeId: string): Observable<readonly GradeHistoryView[]> {
    return this.http.get<readonly GradeHistoryView[]>(`${this.base}/grades/${gradeId}/history`);
  }

  evaluateCompetency(
    command: Readonly<Record<string, unknown>>,
  ): Observable<CompetencyEvaluationView> {
    return this.http.post<CompetencyEvaluationView>(`${this.base}/competency-evaluations`, command);
  }

  createTeacherComment(command: Readonly<Record<string, unknown>>): Observable<TeacherCommentView> {
    return this.http.post<TeacherCommentView>(`${this.base}/teacher-comments`, command);
  }

  calculateReportCards(
    classroomId: string,
    academicTermId: string,
    templateId?: string,
  ): Observable<readonly ReportCardView[]> {
    let params = new HttpParams()
      .set('classroomId', classroomId)
      .set('academicTermId', academicTermId);
    if (templateId) params = params.set('templateId', templateId);
    return this.http.post<readonly ReportCardView[]>(
      `${this.base}/report-cards/calculate`,
      {},
      { params },
    );
  }

  previewReportCard(reportCardId: string, command: ReportCardPublicationCommand): Observable<Blob> {
    return this.http.post(`${this.base}/report-cards/${reportCardId}/preview`, command, {
      responseType: 'blob',
    });
  }

  publishReportCard(
    reportCardId: string,
    command: ReportCardPublicationCommand,
  ): Observable<ReportCardPublicationView> {
    return this.http.post<ReportCardPublicationView>(
      `${this.base}/report-cards/${reportCardId}/publish`,
      command,
    );
  }

  downloadReportCard(reportCardId: string): Observable<Blob> {
    return this.http.get(`${this.base}/report-cards/${reportCardId}/pdf`, {
      responseType: 'blob',
    });
  }

  reportCardVersions(reportCardId: string): Observable<readonly ReportCardPublicationView[]> {
    return this.http.get<readonly ReportCardPublicationView[]>(
      `${this.base}/report-cards/${reportCardId}/versions`,
    );
  }
}
