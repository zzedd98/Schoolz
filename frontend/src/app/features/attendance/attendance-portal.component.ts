import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  AttendanceApiService,
  AttendanceNotification,
  ParentAbsence,
  ParentAttendanceDashboard,
} from '../../core/attendance/attendance-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-attendance-portal',
  imports: [FormsModule, MatButtonModule, RouterLink],
  templateUrl: './attendance-portal.component.html',
  styleUrl: './attendance-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendancePortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(AttendanceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly studentId = this.route.snapshot.queryParamMap.get('studentId');

  protected readonly dashboard = signal<ParentAttendanceDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly selectedAbsence = signal<ParentAbsence | null>(null);
  protected readonly from = signal(this.localDate(new Date(Date.now() - 30 * 86400000)));
  protected readonly to = signal(this.localDate(new Date()));
  protected reasonId = '';
  protected statement = '';
  protected evidenceReference = '';

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected selectPeriod(field: 'from' | 'to', event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    (field === 'from' ? this.from : this.to).set(value);
    this.load();
  }

  protected openJustification(absence: ParentAbsence): void {
    this.selectedAbsence.set(absence);
    this.reasonId = this.dashboard()?.absenceReasons[0]?.id ?? '';
    this.statement = absence.parentStatement ?? '';
    this.evidenceReference = '';
  }

  protected submitJustification(): void {
    const absence = this.selectedAbsence();
    if (!absence || !this.reasonId || this.statement.trim().length < 10) return;
    this.busy.set(true);
    this.api
      .justify(absence.absenceId, {
        reasonId: this.reasonId,
        statement: this.statement,
        evidenceObjectKey: this.evidenceReference || undefined,
      })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.selectedAbsence.set(null);
          this.message.set(
            this.text('Justification envoyée à l’administration.', 'تم إرسال التبرير إلى الإدارة.'),
          );
          this.load();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? this.text("L'envoi a échoué.", 'تعذر الإرسال.')),
      });
  }

  protected read(notification: AttendanceNotification): void {
    if (notification.status === 'READ') return;
    this.api
      .readNotification(notification.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() =>
        this.dashboard.update((dashboard) =>
          dashboard
            ? {
                ...dashboard,
                notifications: dashboard.notifications.map((current) =>
                  current.id === notification.id ? { ...current, status: 'READ' } : current,
                ),
              }
            : dashboard,
        ),
      );
  }

  protected status(absence: ParentAbsence): string {
    const labels: Record<string, string> = {
      UNEXCUSED: this.text('À justifier', 'يحتاج تبريراً'),
      PENDING: this.text('En cours de vérification', 'قيد المراجعة'),
      EXCUSED: this.text('Justifiée', 'مبررة'),
      REJECTED: this.text('À corriger', 'يحتاج تصحيحاً'),
    };
    return labels[absence.resolutionStatus] ?? absence.resolutionStatus;
  }

  protected unreadCount(): number {
    return this.dashboard()?.notifications.filter((item) => item.status === 'UNREAD').length ?? 0;
  }

  protected excusedCount(): number {
    return (
      this.dashboard()?.absences.filter((item) => item.resolutionStatus === 'EXCUSED').length ?? 0
    );
  }

  private load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .parentDashboard(this.from(), this.to())
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) =>
          this.dashboard.set(
            this.studentId
              ? {
                  ...dashboard,
                  absences: dashboard.absences.filter(
                    (absence) => absence.studentId === this.studentId,
                  ),
                  notifications: dashboard.notifications.filter(
                    (notification) => notification.studentId === this.studentId,
                  ),
                }
              : dashboard,
          ),
        error: () =>
          this.error.set(
            this.text(
              'Connectez-vous au portail parent pour consulter les absences.',
              'سجّل الدخول إلى بوابة الوالدين للاطلاع على الغيابات.',
            ),
          ),
      });
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
