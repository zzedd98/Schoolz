import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { finalize, map, Observable, of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  HomeworkApiService,
  HomeworkAttachment,
  HomeworkDashboard,
  HomeworkReferences,
  HomeworkView,
  LessonContentView,
  TeacherAssignmentOption,
} from '../../core/homework/homework-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-homework-dashboard',
  imports: [FormsModule, MatButtonModule],
  templateUrl: './homework-dashboard.component.html',
  styleUrl: './homework-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeworkDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(HomeworkApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly references = signal<HomeworkReferences | null>(null);
  protected readonly dashboard = signal<HomeworkDashboard | null>(null);
  protected readonly selectedClassroomId = signal('');
  protected readonly selectedTermId = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');

  protected lessonAssignment = '';
  protected lessonDate = '';
  protected lessonTitle = '';
  protected lessonSummary = '';
  protected lessonContent = '';
  protected lessonFile: File | null = null;

  protected homeworkAssignment = '';
  protected homeworkLesson = '';
  protected homeworkTitle = '';
  protected homeworkInstructions = '';
  protected homeworkAssignedOn = '';
  protected homeworkDueAt = '';
  protected homeworkMinutes = 30;
  protected homeworkFile: File | null = null;
  private focusActionApplied = false;

  constructor() {
    this.loadReferences();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectClassroom(event: Event): void {
    this.selectedClassroomId.set((event.target as HTMLSelectElement).value);
    this.initializeScope();
  }

  protected selectTerm(event: Event): void {
    this.selectedTermId.set((event.target as HTMLSelectElement).value);
    const term = this.availableTerms().find((item) => item.id === this.selectedTermId());
    if (term) this.initializeDates(term.startsOn, term.endsOn);
    this.loadDashboard();
  }

  protected selectRange(field: 'from' | 'to', event: Event): void {
    (field === 'from' ? this.from : this.to).set((event.target as HTMLInputElement).value);
    this.loadDashboard();
  }

  protected availableTerms() {
    const classroom = this.references()?.classrooms.find(
      (item) => item.id === this.selectedClassroomId(),
    );
    return (
      this.references()?.terms.filter(
        (item) => item.academicYearId === classroom?.academicYearId,
      ) ?? []
    );
  }

  protected availableAssignments(): readonly TeacherAssignmentOption[] {
    return (
      this.references()?.assignments.filter(
        (item) => item.classroomId === this.selectedClassroomId(),
      ) ?? []
    );
  }

  protected publishedLessons(): readonly LessonContentView[] {
    return this.dashboard()?.lessonContents.filter((item) => item.status === 'PUBLISHED') ?? [];
  }

  protected selectFile(owner: 'LESSON' | 'HOMEWORK', event: Event): void {
    const file = (event.target as HTMLInputElement).files?.item(0) ?? null;
    if (owner === 'LESSON') this.lessonFile = file;
    else this.homeworkFile = file;
  }

  protected createLesson(): void {
    if (
      !this.lessonAssignment ||
      !this.selectedTermId() ||
      !this.lessonDate ||
      !this.lessonTitle.trim() ||
      !this.lessonContent.trim()
    )
      return;
    this.run(
      this.api
        .createLesson({
          teacherAssignmentId: this.lessonAssignment,
          academicTermId: this.selectedTermId(),
          lessonDate: this.lessonDate,
          title: this.lessonTitle,
          summary: this.lessonSummary || undefined,
          content: this.lessonContent,
        })
        .pipe(
          switchMap((created) =>
            this.lessonFile
              ? this.api
                  .uploadAttachment('lesson-contents', created.id, this.lessonFile)
                  .pipe(map(() => created))
              : of(created),
          ),
        ),
      () => {
        this.lessonTitle = '';
        this.lessonSummary = '';
        this.lessonContent = '';
        this.lessonFile = null;
        this.reload(
          this.text('Contenu de cours créé en brouillon.', 'تم إنشاء محتوى الدرس كمسودة.'),
        );
      },
    );
  }

  protected publishLesson(lesson: LessonContentView): void {
    this.run(this.api.publishLesson(lesson.id), () =>
      this.reload(this.text('Contenu publié aux familles.', 'تم نشر محتوى الدرس للأسر.')),
    );
  }

  protected createHomework(): void {
    if (
      !this.homeworkAssignment ||
      !this.selectedTermId() ||
      !this.homeworkTitle.trim() ||
      !this.homeworkInstructions.trim() ||
      !this.homeworkAssignedOn ||
      !this.homeworkDueAt
    )
      return;
    this.run(
      this.api
        .createHomework({
          teacherAssignmentId: this.homeworkAssignment,
          academicTermId: this.selectedTermId(),
          lessonContentId: this.homeworkLesson || null,
          title: this.homeworkTitle,
          instructions: this.homeworkInstructions,
          assignedOn: this.homeworkAssignedOn,
          dueAt: `${this.homeworkDueAt}:00+01:00`,
          estimatedMinutes: Number(this.homeworkMinutes) || null,
        })
        .pipe(
          switchMap((created) =>
            this.homeworkFile
              ? this.api
                  .uploadAttachment('assignments', created.id, this.homeworkFile)
                  .pipe(map(() => created))
              : of(created),
          ),
        ),
      () => {
        this.homeworkTitle = '';
        this.homeworkInstructions = '';
        this.homeworkFile = null;
        this.reload(this.text('Devoir créé en brouillon.', 'تم إنشاء الواجب كمسودة.'));
      },
    );
  }

  protected publishHomework(homework: HomeworkView): void {
    this.run(this.api.publishHomework(homework.id), () =>
      this.reload(this.text('Devoir publié aux élèves.', 'تم نشر الواجب للتلاميذ.')),
    );
  }

  protected closeHomework(homework: HomeworkView): void {
    this.run(this.api.closeHomework(homework.id), () =>
      this.reload(this.text('Devoir clôturé.', 'تم إغلاق الواجب.')),
    );
  }

  protected download(attachment: HomeworkAttachment): void {
    this.run(this.api.downloadAttachment(attachment.id), (blob) =>
      this.saveBlob(blob, attachment.fileName),
    );
  }

  protected subjectName(item: { subjectNameFr: string; subjectNameAr: string | null }): string {
    return this.i18n.locale() === 'ar-MA' && item.subjectNameAr
      ? item.subjectNameAr
      : item.subjectNameFr;
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, [string, string]> = {
      DRAFT: ['Brouillon', 'مسودة'],
      PUBLISHED: ['Publié', 'منشور'],
      CLOSED: ['Clôturé', 'مغلق'],
    };
    const label = labels[status] ?? [status, status];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(new Date(value.length === 10 ? `${value}T12:00:00` : value));
  }

  protected completion(homework: HomeworkView): number {
    return homework.totalStudents
      ? Math.round((homework.completedStudents * 100) / homework.totalStudents)
      : 0;
  }

  private loadReferences(): void {
    this.run(this.api.references(), (references) => {
      this.references.set(references);
      this.selectedClassroomId.set(
        references.classrooms.find(
          (item) => item.id === this.route.snapshot.queryParamMap.get('classroomId'),
        )?.id ??
          references.classrooms[0]?.id ??
          '',
      );
      this.initializeScope();
    });
  }

  private initializeScope(): void {
    const term = this.availableTerms()[0];
    this.selectedTermId.set(term?.id ?? '');
    if (term) this.initializeDates(term.startsOn, term.endsOn);
    const assignment =
      this.availableAssignments().find(
        (item) => item.id === this.route.snapshot.queryParamMap.get('teacherAssignmentId'),
      ) ?? this.availableAssignments()[0];
    this.lessonAssignment = assignment?.id ?? '';
    this.homeworkAssignment = assignment?.id ?? '';
    this.loadDashboard();
  }

  private initializeDates(startsOn: string, endsOn: string): void {
    this.from.set(startsOn);
    this.to.set(endsOn);
    const requested = this.route.snapshot.queryParamMap.get('date');
    const selected =
      requested && requested >= startsOn && requested <= endsOn ? requested : startsOn;
    this.lessonDate = selected;
    this.homeworkAssignedOn = selected;
    const due = new Date(`${selected}T18:00:00`);
    due.setDate(due.getDate() + 3);
    this.homeworkDueAt = `${due.getFullYear()}-${String(due.getMonth() + 1).padStart(2, '0')}-${String(due.getDate()).padStart(2, '0')}T18:00`;
  }

  private loadDashboard(): void {
    if (!this.selectedClassroomId() || !this.selectedTermId()) return;
    this.run(
      this.api.dashboard(this.selectedClassroomId(), this.selectedTermId(), this.from(), this.to()),
      (dashboard) => {
        this.dashboard.set(dashboard);
        this.homeworkLesson =
          dashboard.lessonContents.find((item) => item.status === 'PUBLISHED')?.id ?? '';
        if (!this.focusActionApplied && this.route.snapshot.queryParamMap.get('action') === 'add') {
          this.focusActionApplied = true;
          setTimeout(() =>
            document.getElementById('homework-editor')?.scrollIntoView({
              behavior: 'smooth',
              block: 'start',
            }),
          );
        }
      },
    );
  }

  private reload(message: string): void {
    this.message.set(message);
    this.loadDashboard();
  }

  private run<T>(request: Observable<T>, next: (value: T) => void): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next,
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text(
                'Le cahier de texte n’a pas pu être mis à jour.',
                'تعذر تحديث دفتر النصوص.',
              ),
          ),
      });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
