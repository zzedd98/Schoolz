import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { finalize, Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  AssessmentView,
  CompetencyLevel,
  GradeHistoryView,
  GradeView,
  GradingApiService,
  GradingDashboard,
  GradingReferences,
  ReportCardPublicationCommand,
  ReportCardPublicationView,
  ReportCardView,
  StudentView,
} from '../../core/grading/grading-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

type GradingTab = 'GRADEBOOK' | 'COMPETENCIES' | 'RESULTS' | 'CONFIGURATION';

@Component({
  selector: 'app-grading-dashboard',
  imports: [FormsModule, MatButtonModule],
  templateUrl: './grading-dashboard.component.html',
  styleUrl: './grading-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GradingDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(GradingApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly references = signal<GradingReferences | null>(null);
  protected readonly dashboard = signal<GradingDashboard | null>(null);
  protected readonly selectedClassroomId = signal('');
  protected readonly selectedTermId = signal('');
  protected readonly activeTab = signal<GradingTab>('GRADEBOOK');
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly gradeEditor = signal<{
    assessment: AssessmentView;
    student: StudentView;
  } | null>(null);
  protected readonly history = signal<readonly GradeHistoryView[] | null>(null);
  protected readonly historyTitle = signal('');
  protected readonly publicationEditor = signal<ReportCardView | null>(null);
  protected readonly reportVersions = signal<readonly ReportCardPublicationView[]>([]);

  protected assessmentTitle = '';
  protected assessmentSubject = '';
  protected assessmentType = '';
  protected assessmentTeacher = '';
  protected assessmentDate =
    this.route.snapshot.queryParamMap.get('date') ?? new Date().toISOString().slice(0, 10);
  protected assessmentMaximum = 20;
  protected assessmentCoefficient = 1;
  protected assessmentDescription = '';

  protected gradeScore: number | null = null;
  protected gradeStatus = 'GRADED';
  protected gradeComment = '';
  protected gradeReason = '';

  protected competencyStudent = '';
  protected competencyId = '';
  protected competencyTeacher = '';
  protected competencyLevel = '';
  protected competencyObservation = '';

  protected commentStudent = '';
  protected commentSubject = '';
  protected commentTeacher = '';
  protected commentType = 'GENERAL';
  protected commentBody = '';
  protected commentVisible = true;

  protected selectedTemplate = '';
  protected publicationLanguage: 'FR' | 'AR' | 'BILINGUAL' = 'BILINGUAL';
  protected publicationSigner = '';
  protected publicationSignerTitle = '';
  protected publicationNote = '';

  protected configSchoolLevel = '';
  protected policyMode = 'HYBRID';
  protected policyStrategy = 'CATEGORY_WEIGHTED';
  protected policyNumericScale = '';
  protected policyCompetencyScale = '';
  protected policyContinuousWeight = 60;
  protected policyExamWeight = 40;
  protected policyRankings = false;
  protected policyReportCards = true;

  protected scaleCode = '';
  protected scaleName = '';
  protected scaleMaximum = 20;
  protected scalePassing = 10;

  protected typeCode = '';
  protected typeName = '';
  protected typeCategory = 'CONTINUOUS';

  protected coefficientGrade = '';
  protected coefficientSubject = '';
  protected coefficientValue = 1;

  protected newCompetencyCode = '';
  protected newCompetencyName = '';
  protected newCompetencySubject = '';

  protected templateCode = '';
  protected templateName = '';
  protected templateLanguage = 'BILINGUAL';

  constructor() {
    this.loadReferences(true);
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectClassroom(event: Event): void {
    const classroomId = (event.target as HTMLSelectElement).value;
    this.selectedClassroomId.set(classroomId);
    const refs = this.references();
    const classroom = refs?.classrooms.find((value) => value.id === classroomId);
    const firstTerm = refs?.terms.find(
      (value) => value.academicYearId === classroom?.academicYearId,
    );
    if (firstTerm) this.selectedTermId.set(firstTerm.id);
    this.loadDashboard();
  }

  protected selectTerm(event: Event): void {
    this.selectedTermId.set((event.target as HTMLSelectElement).value);
    this.loadDashboard();
  }

  protected availableTerms() {
    const refs = this.references();
    const classroom = refs?.classrooms.find((value) => value.id === this.selectedClassroomId());
    return refs?.terms.filter((value) => value.academicYearId === classroom?.academicYearId) ?? [];
  }

  protected canUseNumericGrades(): boolean {
    const mode = this.dashboard()?.policy.evaluationMode;
    return mode === 'NUMERIC' || mode === 'HYBRID';
  }

  protected canUseCompetencies(): boolean {
    const mode = this.dashboard()?.policy.evaluationMode;
    return mode === 'COMPETENCY_ONLY' || mode === 'HYBRID';
  }

  protected policyLabel(mode: string): string {
    const labels: Record<string, [string, string]> = {
      NONE: ['Sans notes', 'بدون نقط'],
      COMPETENCY_ONLY: ['Compétences', 'الكفايات'],
      HYBRID: ['Notes + compétences', 'النقط والكفايات'],
      NUMERIC: ['Notes numériques', 'النقط الرقمية'],
    };
    const label = labels[mode] ?? [mode, mode];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
  }

  protected strategyLabel(strategy: string): string {
    return strategy === 'CATEGORY_WEIGHTED'
      ? this.text('Moyenne par catégories', 'معدل حسب الفئات')
      : this.text('Moyenne pondérée', 'معدل موزون');
  }

  protected gradeFor(assessmentId: string, studentId: string): GradeView | undefined {
    return this.dashboard()?.grades.find(
      (grade) => grade.assessmentId === assessmentId && grade.studentId === studentId,
    );
  }

  protected openGradeEditor(assessment: AssessmentView, student: StudentView): void {
    const grade = this.gradeFor(assessment.id, student.id);
    this.gradeScore = grade?.rawScore ?? null;
    this.gradeStatus = grade?.status ?? 'GRADED';
    this.gradeComment = grade?.teacherComment ?? '';
    this.gradeReason = '';
    this.gradeEditor.set({ assessment, student });
    this.error.set('');
  }

  protected closeGradeEditor(): void {
    this.gradeEditor.set(null);
  }

  protected saveGrade(): void {
    const editor = this.gradeEditor();
    if (!editor) return;
    const existing = this.gradeFor(editor.assessment.id, editor.student.id);
    if (existing && !this.gradeReason.trim()) {
      this.error.set(
        this.text(
          'Le motif est obligatoire pour modifier une note existante.',
          'سبب التعديل إجباري عند تغيير نقطة موجودة.',
        ),
      );
      return;
    }
    this.run(
      this.api.saveGrade(editor.assessment.id, editor.student.id, {
        rawScore: this.gradeStatus === 'GRADED' ? this.gradeScore : null,
        status: this.gradeStatus,
        teacherComment: this.gradeComment,
        reason: this.gradeReason || undefined,
      }),
      () => {
        this.gradeEditor.set(null);
        this.reloadDashboard(
          this.text('Note enregistrée et historisée.', 'تم حفظ النقطة وتأريخها.'),
        );
      },
    );
  }

  protected showHistory(assessment: AssessmentView, student: StudentView): void {
    const grade = this.gradeFor(assessment.id, student.id);
    if (!grade) return;
    this.historyTitle.set(`${student.firstName} ${student.lastName} · ${assessment.title}`);
    this.run(this.api.gradeHistory(grade.id), (history) => this.history.set(history));
  }

  protected createAssessment(): void {
    const classroomId = this.selectedClassroomId();
    const termId = this.selectedTermId();
    if (!classroomId || !termId) return;
    this.run(
      this.api.createAssessment({
        classroomId,
        academicTermId: termId,
        subjectId: this.assessmentSubject,
        assessmentTypeId: this.assessmentType,
        teacherStaffId: this.assessmentTeacher,
        title: this.assessmentTitle,
        description: this.assessmentDescription,
        assessmentDate: this.assessmentDate,
        maximumScore: Number(this.assessmentMaximum),
        coefficient: Number(this.assessmentCoefficient),
      }),
      () => {
        this.assessmentTitle = '';
        this.assessmentDescription = '';
        this.reloadDashboard(this.text('Évaluation créée.', 'تم إنشاء التقييم.'));
      },
    );
  }

  protected publishAssessment(assessment: AssessmentView): void {
    this.run(this.api.publishAssessment(assessment.id), () =>
      this.reloadDashboard(this.text('Évaluation publiée.', 'تم نشر التقييم.')),
    );
  }

  protected evaluateCompetency(): void {
    if (!this.competencyStudent || !this.competencyId || !this.competencyLevel) return;
    this.run(
      this.api.evaluateCompetency({
        competencyId: this.competencyId,
        studentId: this.competencyStudent,
        academicTermId: this.selectedTermId(),
        teacherStaffId: this.competencyTeacher,
        levelCode: this.competencyLevel,
        observation: this.competencyObservation,
      }),
      () => {
        this.competencyObservation = '';
        this.reloadDashboard(this.text('Compétence évaluée.', 'تم تقييم الكفاية.'));
      },
    );
  }

  protected createComment(): void {
    if (!this.commentStudent || !this.commentTeacher || !this.commentBody.trim()) return;
    this.run(
      this.api.createTeacherComment({
        studentId: this.commentStudent,
        academicTermId: this.selectedTermId(),
        subjectId: this.commentSubject || null,
        teacherStaffId: this.commentTeacher,
        commentType: this.commentType,
        body: this.commentBody,
        visibleToFamily: this.commentVisible,
      }),
      () => {
        this.commentBody = '';
        this.reloadDashboard(this.text('Appréciation ajoutée.', 'تمت إضافة الملاحظة.'));
      },
    );
  }

  protected calculateReportCards(): void {
    this.run(
      this.api.calculateReportCards(
        this.selectedClassroomId(),
        this.selectedTermId(),
        this.selectedTemplate || undefined,
      ),
      () => this.reloadDashboard(this.text('Bulletins recalculés.', 'تمت إعادة حساب البيانات.')),
    );
  }

  protected latestReportCards(): readonly ReportCardView[] {
    const latest = new Map<string, ReportCardView>();
    for (const report of this.dashboard()?.reportCards ?? []) {
      const current = latest.get(report.studentId);
      if (!current || report.versionNumber > current.versionNumber) {
        latest.set(report.studentId, report);
      }
    }
    return [...latest.values()];
  }

  protected publishedReportCount(): number {
    return (this.dashboard()?.reportCards ?? []).filter((report) => report.status === 'PUBLISHED')
      .length;
  }

  protected openPublication(report: ReportCardView): void {
    const template = this.references()?.templates.find((value) => value.id === report.templateId);
    this.publicationLanguage = template?.languageMode ?? report.languageMode ?? 'BILINGUAL';
    this.publicationSigner = report.signedByName ?? this.auth.user()?.displayName ?? '';
    this.publicationSignerTitle = report.signedByTitle ?? this.text('Direction', 'الإدارة');
    this.publicationNote = '';
    this.publicationEditor.set(report);
    this.reportVersions.set([]);
    this.loadReportVersions(report);
  }

  protected closePublication(): void {
    this.publicationEditor.set(null);
    this.reportVersions.set([]);
  }

  protected previewReportCard(): void {
    const report = this.publicationEditor();
    if (!report) return;
    this.run(this.api.previewReportCard(report.id, this.publicationCommand()), (document) => {
      this.openPdf(document);
      this.message.set(this.text('Aperçu PDF généré.', 'تم إنشاء معاينة PDF.'));
    });
  }

  protected publishReportCard(): void {
    const report = this.publicationEditor();
    if (!report) return;
    this.run(this.api.publishReportCard(report.id, this.publicationCommand()), () => {
      this.closePublication();
      this.reloadDashboard(
        this.text('Bulletin publié et version figée.', 'تم نشر البيان وتثبيت نسخته.'),
      );
    });
  }

  protected downloadReportCard(report: ReportCardView | ReportCardPublicationView): void {
    if (report.status !== 'PUBLISHED') return;
    this.run(this.api.downloadReportCard(report.id), (document) => {
      this.savePdf(document, report.fileName ?? `bulletin-v${report.versionNumber}.pdf`);
      this.message.set(this.text('Téléchargement PDF prêt.', 'ملف PDF جاهز للتنزيل.'));
    });
  }

  protected statusLabel(status: string): string {
    return status === 'PUBLISHED'
      ? this.text('Publié', 'منشور')
      : this.text('À valider', 'في انتظار المصادقة');
  }

  protected languageLabel(language: string | null): string {
    if (language === 'AR') return 'العربية';
    if (language === 'BILINGUAL') return 'FR + AR';
    return 'Français';
  }

  protected shortHash(value: string | null): string {
    return value ? `${value.slice(0, 10)}…` : '—';
  }

  protected savePolicy(): void {
    if (!this.configSchoolLevel) return;
    const categoryWeights =
      this.policyStrategy === 'CATEGORY_WEIGHTED'
        ? { CONTINUOUS: Number(this.policyContinuousWeight), EXAM: Number(this.policyExamWeight) }
        : {};
    this.run(
      this.api.savePolicy({
        scopeType: 'SCHOOL_LEVEL',
        schoolLevelId: this.configSchoolLevel,
        code: `POLICY-${this.configSchoolLevel}`,
        name: this.text('Politique académique', 'السياسة الأكاديمية'),
        evaluationMode: this.policyMode,
        gradeScaleId:
          this.policyMode === 'COMPETENCY_ONLY' || this.policyMode === 'NONE'
            ? null
            : this.policyNumericScale || null,
        competencyScaleId:
          this.policyMode === 'NUMERIC' || this.policyMode === 'NONE'
            ? null
            : this.policyCompetencyScale || null,
        calculationStrategy: this.policyStrategy,
        calculationConfig: {
          categoryWeights,
          missingCategoryMode: 'RENORMALIZE',
          roundingScale: 2,
          overallUseCoefficients: true,
          rankingMethod: 'COMPETITION',
        },
        rankingsEnabled: this.policyRankings,
        reportCardsEnabled: this.policyReportCards,
      }),
      () => this.loadReferences(false, this.text('Politique enregistrée.', 'تم حفظ السياسة.')),
    );
  }

  protected createNumericScale(): void {
    this.run(
      this.api.createScale({
        code: this.scaleCode,
        name: this.scaleName,
        scaleType: 'NUMERIC',
        minimumScore: 0,
        maximumScore: Number(this.scaleMaximum),
        passingScore: Number(this.scalePassing),
        roundingScale: 2,
        competencyLevels: [],
      }),
      () => {
        this.scaleCode = '';
        this.scaleName = '';
        this.loadReferences(false, this.text('Barème créé.', 'تم إنشاء سلم التنقيط.'));
      },
    );
  }

  protected createAssessmentType(): void {
    this.run(
      this.api.createAssessmentType({
        code: this.typeCode,
        nameFr: this.typeName,
        calculationCategory: this.typeCategory,
        numericEnabled: true,
        competencyEnabled: false,
      }),
      () => {
        this.typeCode = '';
        this.typeName = '';
        this.loadReferences(false, this.text("Type d'évaluation créé.", 'تم إنشاء نوع التقييم.'));
      },
    );
  }

  protected saveCoefficient(): void {
    const refs = this.references();
    const grade = refs?.gradeLevels.find((value) => value.id === this.coefficientGrade);
    const classroom = refs?.classrooms.find((value) => value.gradeLevelId === grade?.id);
    if (!classroom) return;
    this.run(
      this.api.saveCoefficient({
        academicYearId: classroom.academicYearId,
        gradeLevelId: this.coefficientGrade,
        subjectId: this.coefficientSubject,
        coefficient: Number(this.coefficientValue),
      }),
      () => this.loadReferences(false, this.text('Coefficient enregistré.', 'تم حفظ المعامل.')),
    );
  }

  protected createCompetency(): void {
    if (!this.configSchoolLevel) return;
    this.run(
      this.api.createCompetency({
        schoolLevelId: this.configSchoolLevel,
        subjectId: this.newCompetencySubject || null,
        code: this.newCompetencyCode,
        nameFr: this.newCompetencyName,
        sequenceNumber: (this.references()?.competencies.length ?? 0) + 1,
      }),
      () => {
        this.newCompetencyCode = '';
        this.newCompetencyName = '';
        this.loadReferences(false, this.text('Compétence créée.', 'تم إنشاء الكفاية.'));
      },
    );
  }

  protected createTemplate(): void {
    if (!this.configSchoolLevel) return;
    this.run(
      this.api.createTemplate({
        schoolLevelId: this.configSchoolLevel,
        code: this.templateCode,
        name: this.templateName,
        languageMode: this.templateLanguage,
        layoutConfig: { showRank: this.policyRankings, showCompetencies: true },
        versionNumber: 1,
      }),
      () => {
        this.templateCode = '';
        this.templateName = '';
        this.loadReferences(false, this.text('Modèle de bulletin créé.', 'تم إنشاء نموذج البيان.'));
      },
    );
  }

  protected competencyLevels(): readonly CompetencyLevel[] {
    const policy = this.dashboard()?.policy;
    return (
      this.references()?.scales.find((scale) => scale.id === policy?.competencyScaleId)
        ?.competencyLevels ?? []
    );
  }

  protected levelLabel(code: string): string {
    const level = this.competencyLevels().find((value) => value.code === code);
    if (!level) return code;
    return this.i18n.locale() === 'ar-MA' ? level.labelAr || level.labelFr : level.labelFr;
  }

  protected templatesForClass() {
    const schoolLevelId = this.dashboard()?.context.schoolLevelId;
    return (
      this.references()?.templates.filter((value) => value.schoolLevelId === schoolLevelId) ?? []
    );
  }

  protected summaryAverage(studentId: string): number | null {
    return (
      this.dashboard()?.summaries.find((value) => value.studentId === studentId)?.overallAverage ??
      null
    );
  }

  protected formatAverage(value: number | null): string {
    return value === null ? '—' : Number(value).toFixed(2);
  }

  protected configureLevel(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.configSchoolLevel = id;
    const policy = this.references()?.policies.find((value) => value.schoolLevelId === id);
    if (!policy) return;
    this.policyMode = policy.evaluationMode;
    this.policyStrategy = policy.calculationStrategy;
    this.policyNumericScale = policy.gradeScaleId ?? '';
    this.policyCompetencyScale = policy.competencyScaleId ?? '';
    const weights = policy.calculationConfig['categoryWeights'] as
      Record<string, number> | undefined;
    this.policyContinuousWeight = weights?.['CONTINUOUS'] ?? 60;
    this.policyExamWeight = weights?.['EXAM'] ?? 40;
    this.policyRankings = policy.rankingsEnabled;
    this.policyReportCards = policy.reportCardsEnabled;
  }

  private loadReferences(initial: boolean, successMessage = ''): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .references()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (references) => {
          this.references.set(references);
          if (initial) this.prepareInitialSelection(references);
          else {
            this.seedForms(references);
            if (successMessage) this.message.set(successMessage);
            if (this.selectedClassroomId()) this.loadDashboard();
          }
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private prepareInitialSelection(references: GradingReferences): void {
    const year = references.years.find((value) => value.status === 'ACTIVE') ?? references.years[0];
    const classroom =
      references.classrooms.find(
        (value) => value.id === this.route.snapshot.queryParamMap.get('classroomId'),
      ) ??
      references.classrooms.find((value) => value.academicYearId === year?.id) ??
      references.classrooms[0];
    const term = references.terms.find(
      (value) => value.academicYearId === classroom?.academicYearId,
    );
    this.selectedClassroomId.set(classroom?.id ?? '');
    this.selectedTermId.set(term?.id ?? '');
    this.configSchoolLevel = classroom?.schoolLevelId ?? references.schoolLevels[0]?.id ?? '';
    this.seedForms(references);
    if (classroom && term) this.loadDashboard();
  }

  private seedForms(references: GradingReferences): void {
    this.assessmentSubject ||=
      references.subjects.find(
        (value) => value.id === this.route.snapshot.queryParamMap.get('subjectId'),
      )?.id ??
      references.subjects[0]?.id ??
      '';
    this.assessmentType ||=
      references.assessmentTypes.find((value) => value.numericEnabled)?.id ?? '';
    this.assessmentTeacher ||=
      references.teachers.find(
        (value) => value.id === this.route.snapshot.queryParamMap.get('teacherStaffId'),
      )?.id ??
      references.teachers[0]?.id ??
      '';
    this.competencyTeacher ||= references.teachers[0]?.id ?? '';
    this.commentTeacher ||= references.teachers[0]?.id ?? '';
    this.commentSubject ||= references.subjects[0]?.id ?? '';
    this.policyNumericScale ||=
      references.scales.find((value) => value.scaleType === 'NUMERIC')?.id ?? '';
    this.policyCompetencyScale ||=
      references.scales.find((value) => value.scaleType === 'COMPETENCY')?.id ?? '';
    this.coefficientGrade ||= references.gradeLevels[0]?.id ?? '';
    this.coefficientSubject ||= references.subjects[0]?.id ?? '';
    this.newCompetencySubject ||= references.subjects[0]?.id ?? '';
    if (this.configSchoolLevel) {
      const fakeEvent = { target: { value: this.configSchoolLevel } } as unknown as Event;
      this.configureLevel(fakeEvent);
    }
  }

  private loadDashboard(): void {
    const classroomId = this.selectedClassroomId();
    const termId = this.selectedTermId();
    if (!classroomId || !termId) {
      this.dashboard.set(null);
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api
      .dashboard(classroomId, termId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.competencyStudent ||= dashboard.students[0]?.id ?? '';
          this.commentStudent ||= dashboard.students[0]?.id ?? '';
          this.competencyId ||= dashboard.competencies[0]?.id ?? '';
          this.competencyLevel ||= this.competencyLevels()[0]?.code ?? '';
          this.selectedTemplate ||= this.templatesForClass()[0]?.id ?? '';
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private reloadDashboard(successMessage: string): void {
    this.message.set(successMessage);
    this.loadDashboard();
  }

  private publicationCommand(): ReportCardPublicationCommand {
    return {
      languageMode: this.publicationLanguage,
      signerName: this.publicationSigner,
      signerTitle: this.publicationSignerTitle || undefined,
      publicationNote: this.publicationNote || undefined,
    };
  }

  private loadReportVersions(report: ReportCardView): void {
    this.run(this.api.reportCardVersions(report.id), (versions) =>
      this.reportVersions.set(versions),
    );
  }

  private openPdf(document: Blob): void {
    const url = URL.createObjectURL(document);
    const link = window.document.createElement('a');
    link.href = url;
    link.target = '_blank';
    link.rel = 'noopener';
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  private savePdf(document: Blob, fileName: string): void {
    const url = URL.createObjectURL(document);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private run<T>(request: Observable<T>, success: (value: T) => void): void {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: success,
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private handleError(error: HttpErrorResponse): void {
    const message = (error.error as { message?: string } | null)?.message;
    this.error.set(
      message || this.text("L'opération n'a pas pu être terminée.", 'تعذر إتمام العملية.'),
    );
  }
}
