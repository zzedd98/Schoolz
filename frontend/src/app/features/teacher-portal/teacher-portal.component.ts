import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { LocaleService } from '../../core/i18n/locale.service';
import { PwaInstallService } from '../../core/pwa/pwa-install.service';
import {
  TeacherPortalAction,
  TeacherPortalApiService,
  TeacherPortalDashboard,
  TeacherPortalLesson,
} from '../../core/teacher-portal/teacher-portal-api.service';

@Component({
  selector: 'app-teacher-portal',
  imports: [MatButtonModule],
  templateUrl: './teacher-portal.component.html',
  styleUrl: './teacher-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeacherPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(TeacherPortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pwa = inject(PwaInstallService);
  private autoAdvanceToNextTeachingDate = !this.route.snapshot.queryParamMap.has('date');

  protected readonly selectedDate = signal(
    this.route.snapshot.queryParamMap.get('date') ?? this.localDate(new Date()),
  );
  protected readonly dashboard = signal<TeacherPortalDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly installHint = signal(false);
  protected readonly standalone = this.pwa.standalone;

  constructor() {
    this.pwa.activate('teacher');
    this.load();
  }

  protected async install(): Promise<void> {
    this.installHint.set((await this.pwa.install()) === 'unavailable');
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectDate(event: Event): void {
    this.goToDate((event.target as HTMLInputElement).value);
  }

  protected shiftDate(days: number): void {
    const date = new Date(`${this.selectedDate()}T12:00:00`);
    date.setDate(date.getDate() + days);
    this.goToDate(this.localDate(date));
  }

  protected goToDate(date: string): void {
    if (!date) return;
    this.selectedDate.set(date);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { date },
      replaceUrl: true,
    });
    this.load();
  }

  protected runAction(action: TeacherPortalAction): void {
    if (action.enabled && action.targetPath) void this.router.navigateByUrl(action.targetPath);
  }

  protected lessonAction(lesson: TeacherPortalLesson): void {
    const call = this.dashboard()?.quickActions.find((action) => action.code === 'TAKE_ATTENDANCE');
    if (!call?.enabled || !call.targetPath) return;
    const target = new URL(call.targetPath, 'http://schoolz.local');
    target.searchParams.set('classroomId', lesson.classroomId);
    target.searchParams.set('teacherStaffId', this.dashboard()?.teacher.staffProfileId ?? '');
    target.searchParams.set('date', this.selectedDate());
    target.searchParams.set('startsAt', lesson.startsAt);
    target.searchParams.set('endsAt', lesson.endsAt);
    target.searchParams.set('subjectCode', lesson.subjectCode);
    target.searchParams.set('subjectName', lesson.subjectNameFr);
    if (lesson.attendanceSessionId)
      target.searchParams.set('sessionId', lesson.attendanceSessionId);
    void this.router.navigateByUrl(`${target.pathname}${target.search}`);
  }

  protected subjectName(lesson: TeacherPortalLesson): string {
    return this.i18n.locale() === 'ar-MA' && lesson.subjectNameAr
      ? lesson.subjectNameAr
      : lesson.subjectNameFr;
  }

  protected actionLabel(action: TeacherPortalAction): string {
    const labels: Record<TeacherPortalAction['code'], [string, string]> = {
      TAKE_ATTENDANCE: ["Faire l'appel", 'تسجيل الحضور'],
      ENTER_GRADES: ['Saisir notes', 'إدخال النقط'],
      ADD_HOMEWORK: ['Ajouter devoir', 'إضافة واجب'],
      OBSERVATION: ['Observation', 'ملاحظة'],
      MESSAGE: ['Message', 'رسالة'],
    };
    return this.i18n.locale() === 'ar-MA' ? labels[action.code][1] : labels[action.code][0];
  }

  protected actionIcon(code: TeacherPortalAction['code']): string {
    return {
      TAKE_ATTENDANCE: '✓',
      ENTER_GRADES: 'A+',
      ADD_HOMEWORK: '+',
      OBSERVATION: '◉',
      MESSAGE: '↗',
    }[code];
  }

  protected timelineLabel(status: TeacherPortalLesson['timelineStatus']): string {
    const labels: Record<TeacherPortalLesson['timelineStatus'], [string, string]> = {
      COMPLETED: ['Terminé', 'منتهية'],
      IN_PROGRESS: ['En cours', 'جارية'],
      NEXT: ['Prochain', 'التالية'],
      UPCOMING: ['À venir', 'لاحقاً'],
    };
    return this.i18n.locale() === 'ar-MA' ? labels[status][1] : labels[status][0];
  }

  protected displayDate(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(new Date(`${value}T12:00:00`));
  }

  protected isToday(): boolean {
    return this.selectedDate() === this.localDate(new Date());
  }

  private load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .today(this.selectedDate())
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          if (
            this.autoAdvanceToNextTeachingDate &&
            dashboard.lessons.length === 0 &&
            dashboard.nextTeachingDate
          ) {
            this.autoAdvanceToNextTeachingDate = false;
            this.goToDate(dashboard.nextTeachingDate);
            return;
          }
          this.autoAdvanceToNextTeachingDate = false;
          this.dashboard.set(dashboard);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text('Votre journée ne peut pas être chargée.', 'تعذر تحميل برنامج يومك.'),
          ),
      });
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
