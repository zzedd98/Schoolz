import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import {
  DirectionAnalyticsApiService,
  DirectionDashboard,
} from '../../core/analytics/direction-analytics-api.service';

@Component({
  selector: 'app-direction-analytics',
  templateUrl: './direction-analytics.component.html',
  styleUrl: './direction-analytics.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionAnalyticsComponent {
  private readonly api = inject(DirectionAnalyticsApiService);

  protected readonly data = signal<DirectionDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly asOf = signal('2026-08-10');
  protected readonly campusId = signal('');
  protected readonly maxEnrollment = computed(() =>
    Math.max(1, ...(this.data()?.enrollmentTrend.map((point) => point.totalStudents) ?? [1])),
  );
  protected readonly maxExpected = computed(() =>
    Math.max(1, ...(this.data()?.collectionTrend.map((point) => point.expected) ?? [1])),
  );
  protected readonly maxAbsence = computed(() =>
    Math.max(1, ...(this.data()?.absenteeismTrend.map((point) => point.absenceRate) ?? [1])),
  );

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.direction(this.asOf(), this.campusId() || undefined).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Les indicateurs de direction ne peuvent pas être chargés pour le moment.');
        this.loading.set(false);
      },
    });
  }

  protected changeDate(event: Event): void {
    this.asOf.set((event.target as HTMLInputElement).value);
    this.refresh();
  }

  protected changeCampus(event: Event): void {
    this.campusId.set((event.target as HTMLSelectElement).value);
    this.refresh();
  }

  protected relative(value: number, maximum: number, minimum = 6): number {
    if (value <= 0 || maximum <= 0) return 0;
    return Math.max(minimum, Math.min(100, (value / maximum) * 100));
  }

  protected dimensionMax(values: readonly { value: number }[]): number {
    return Math.max(1, ...values.map((item) => item.value));
  }

  protected month(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', { month: 'short' })
      .format(new Date(`${value}T12:00:00`))
      .replace('.', '');
  }

  protected money(value: number, compact = false): string {
    return new Intl.NumberFormat('fr-MA', {
      style: 'currency',
      currency: 'MAD',
      notation: compact ? 'compact' : 'standard',
      maximumFractionDigits: compact ? 1 : 0,
    }).format(value);
  }

  protected number(value: number): string {
    return new Intl.NumberFormat('fr-MA').format(value);
  }

  protected rate(value: number): string {
    return `${new Intl.NumberFormat('fr-MA', { maximumFractionDigits: 1 }).format(value)} %`;
  }
}
