import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  HomeworkApiService,
  HomeworkAttachment,
  ParentHomework,
  ParentHomeworkDashboard,
} from '../../core/homework/homework-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-homework-portal',
  imports: [FormsModule, MatButtonModule, RouterLink],
  templateUrl: './homework-portal.component.html',
  styleUrl: './homework-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeworkPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(HomeworkApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dashboard = signal<ParentHomeworkDashboard | null>(null);
  protected readonly from = signal(this.localDate(new Date(Date.now() - 7 * 86400000)));
  protected readonly to = signal(this.localDate(new Date(Date.now() + 60 * 86400000)));
  protected readonly selectedStudentId = signal(
    this.route.snapshot.queryParamMap.get('studentId') ?? '',
  );
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected selectDate(field: 'from' | 'to', event: Event): void {
    (field === 'from' ? this.from : this.to).set((event.target as HTMLInputElement).value);
    this.load();
  }

  protected selectStudent(event: Event): void {
    this.selectedStudentId.set((event.target as HTMLSelectElement).value);
    this.load();
  }

  protected dueDays(): readonly string[] {
    return [
      ...new Set((this.dashboard()?.homework ?? []).map((item) => this.datePart(item.dueAt))),
    ].sort();
  }

  protected homeworkFor(date: string): readonly ParentHomework[] {
    return (this.dashboard()?.homework ?? []).filter((item) => this.datePart(item.dueAt) === date);
  }

  protected todoCount(): number {
    return this.dashboard()?.homework.filter((item) => item.studentStatus === 'TO_DO').length ?? 0;
  }

  protected doneCount(): number {
    return this.dashboard()?.homework.filter((item) => item.studentStatus === 'DONE').length ?? 0;
  }

  protected overdueCount(): number {
    return this.dashboard()?.homework.filter((item) => item.overdue).length ?? 0;
  }

  protected toggleStatus(homework: ParentHomework): void {
    const next = homework.studentStatus === 'DONE' ? 'TO_DO' : 'DONE';
    this.busy.set(true);
    this.error.set('');
    this.api
      .markStatus(homework.id, homework.studentId, next)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.dashboard.update((dashboard) =>
            dashboard
              ? {
                  ...dashboard,
                  homework: dashboard.homework.map((current) =>
                    current.id === updated.id && current.studentId === updated.studentId
                      ? updated
                      : current,
                  ),
                }
              : dashboard,
          );
          this.message.set(
            next === 'DONE'
              ? this.text('Le devoir est marqué comme fait.', 'تم وضع علامة منجز على الواجب.')
              : this.text('Le devoir est de nouveau à faire.', 'أصبح الواجب مطلوباً من جديد.'),
          );
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text('Le statut n’a pas été modifié.', 'لم يتم تغيير الحالة.'),
          ),
      });
  }

  protected download(attachment: HomeworkAttachment): void {
    this.busy.set(true);
    this.api
      .downloadAttachment(attachment.id, true)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = attachment.fileName;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: () =>
          this.error.set(this.text('Le fichier n’est pas disponible.', 'الملف غير متاح.')),
      });
  }

  protected subjectName(item: { subjectNameFr: string; subjectNameAr: string | null }): string {
    return this.i18n.locale() === 'ar-MA' && item.subjectNameAr
      ? item.subjectNameAr
      : item.subjectNameFr;
  }

  protected formatDate(value: string, includeTime = false): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      weekday: 'short',
      day: '2-digit',
      month: 'long',
      ...(includeTime ? { hour: '2-digit', minute: '2-digit' } : {}),
    }).format(new Date(value.length === 10 ? `${value}T12:00:00` : value));
  }

  private load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .parentDashboard(this.from(), this.to(), this.selectedStudentId() || undefined)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => this.dashboard.set(dashboard),
        error: () => {
          this.dashboard.set(null);
          this.error.set(
            this.text(
              'Connectez-vous au portail famille pour voir le cahier de texte.',
              'سجّل الدخول إلى بوابة الأسرة لعرض دفتر النصوص.',
            ),
          );
        },
      });
  }

  private datePart(value: string): string {
    const date = new Date(value);
    return this.localDate(date);
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
