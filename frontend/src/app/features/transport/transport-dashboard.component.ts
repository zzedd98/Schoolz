import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { LocaleService } from '../../core/i18n/locale.service';
import {
  TransportApiService,
  TransportDashboard,
} from '../../core/transport/transport-api.service';

@Component({
  selector: 'app-transport-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './transport-dashboard.component.html',
  styleUrl: './transport-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransportDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(TransportApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly dashboard = signal<TransportDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected tripId = '';
  protected subscriptionId = '';
  constructor() {
    this.load();
  }
  protected text(fr: string, ar: string): string {
    return this.i18n.locale() === 'ar-MA' ? ar : fr;
  }
  protected pickup(): void {
    if (this.tripId && this.subscriptionId)
      this.mutate(
        this.api.pickup(this.tripId, this.subscriptionId),
        this.text('Montée enregistrée.', 'تم تسجيل الصعود.'),
      );
  }
  protected dropoff(): void {
    if (this.tripId && this.subscriptionId)
      this.mutate(
        this.api.dropoff(this.tripId, this.subscriptionId),
        this.text('Descente enregistrée.', 'تم تسجيل النزول.'),
      );
  }
  protected move(): void {
    if (this.tripId)
      this.mutate(
        this.api.position(this.tripId, 33.585, -7.6),
        this.text('Position GPS reçue.', 'تم استلام موقع GPS.'),
      );
  }
  private load(): void {
    this.busy.set(true);
    this.api
      .dashboard()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (d) => {
          this.dashboard.set(d);
          this.tripId ||= d.trips[0]?.id ?? '';
          this.subscriptionId ||= d.subscriptions[0]?.id ?? '';
        },
        error: (r) => this.failure(r),
      });
  }
  private mutate(request: Observable<unknown>, message: string): void {
    this.busy.set(true);
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notice.set(message);
          this.load();
        },
        error: (r) => this.failure(r),
      });
  }
  private failure(response: { error?: { detail?: string } }): void {
    this.error.set(
      response.error?.detail ??
        this.text('Le transport est indisponible.', 'خدمة النقل غير متاحة.'),
    );
  }
}
