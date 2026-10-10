import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ChildcareApiService,
  ChildcareDailyReport,
  ChildcareEvent,
} from '../../core/childcare/childcare-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-childcare-report',
  imports: [RouterLink],
  templateUrl: './childcare-report.component.html',
  styleUrl: './childcare-report.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChildcareReportComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(ChildcareApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly studentId = this.route.snapshot.queryParamMap.get('studentId');

  protected readonly date = signal(this.localDate(new Date()));
  protected readonly reports = signal<readonly ChildcareDailyReport[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected selectDate(event: Event): void {
    this.date.set((event.target as HTMLInputElement).value);
    this.load();
  }

  protected label(event: ChildcareEvent): string {
    const labels: Record<string, string> = {
      MEAL: this.text('Repas', 'وجبة'),
      BOTTLE: this.text('Biberon', 'رضاعة'),
      SLEEP: this.text('Sieste', 'قيلولة'),
      DIAPER: this.text('Change', 'حفاض'),
      TOILET: this.text('Toilette', 'مرحاض'),
      ACTIVITY: this.text('Activité', 'نشاط'),
      MOOD: this.text('Humeur', 'مزاج'),
      OBSERVATION: this.text('Observation', 'ملاحظة'),
      INCIDENT: this.text('Incident', 'حادث'),
      TEMPERATURE: this.text('Température mesurée', 'درجة الحرارة المقاسة'),
      MEDICATION: this.text('Médicament', 'دواء'),
    };
    return labels[event.eventType] ?? event.eventType;
  }

  protected detail(event: ChildcareEvent): string {
    if (event.eventType === 'SLEEP')
      return `${this.time(event.occurredAt)} → ${this.time(event.endedAt)}`;
    if (event.eventType === 'TEMPERATURE') return `${event.temperatureCelsius} °C`;
    if (event.eventType === 'MEDICATION')
      return `${event.medicationName ?? ''} ${event.medicationDose ?? ''}`.trim();
    if (event.eventType === 'BOTTLE' && event.quantity)
      return `${event.quantity} ${event.unit ?? 'ml'}`;
    return event.label ?? event.notes ?? event.outcome ?? this.text('Enregistré', 'مسجل');
  }

  protected time(value: string | null): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }

  protected mood(value: string | null): string {
    return (
      (
        { HAPPY: '😊', CALM: '😌', TIRED: '😴', SENSITIVE: '🥺', UPSET: '😟' } as Record<
          string,
          string
        >
      )[value ?? ''] ?? '—'
    );
  }

  private load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .reports(this.date())
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (reports) =>
          this.reports.set(
            this.studentId
              ? reports.filter((report) => report.studentId === this.studentId)
              : reports,
          ),
        error: () =>
          this.error.set(
            this.text(
              'Connectez-vous au portail parent pour consulter les rapports publiés.',
              'سجّل الدخول إلى بوابة الوالدين للاطلاع على التقارير المنشورة.',
            ),
          ),
      });
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
