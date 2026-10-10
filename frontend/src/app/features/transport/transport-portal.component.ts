import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
import {
  FamilyTransportDashboard,
  TransportApiService,
} from '../../core/transport/transport-api.service';

@Component({
  selector: 'app-transport-portal',
  imports: [DatePipe, RouterLink],
  templateUrl: './transport-portal.component.html',
  styleUrl: './transport-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransportPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(TransportApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  protected readonly dashboard = signal<FamilyTransportDashboard | null>(null);
  protected readonly authRequired = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  constructor() {
    this.load();
  }
  protected text(fr: string, ar: string): string {
    return this.i18n.locale() === 'ar-MA' ? ar : fr;
  }
  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }
  protected logout(): void {
    this.admissions
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.redirectToLogin(false),
        error: () => this.redirectToLogin(false),
      });
  }
  private load(): void {
    this.busy.set(true);
    this.api
      .familyDashboard()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (d) => this.accept(d),
        error: (r: { status?: number }) =>
          r.status === 401 ? this.redirectToLogin(true) : this.fail(r),
      });
  }
  private accept(d: FamilyTransportDashboard): void {
    this.dashboard.set(d);
    this.authRequired.set(false);
  }
  private redirectToLogin(returnToPortal: boolean): void {
    this.dashboard.set(null);
    this.authRequired.set(true);
    void this.router.navigate(['/login'], {
      ...(returnToPortal ? { queryParams: { portal: 'family', returnUrl: this.router.url } } : {}),
      replaceUrl: true,
    });
  }
  private fail(r: { status?: number; error?: { detail?: string } }): void {
    this.error.set(
      r.error?.detail ?? this.text('Le suivi transport est indisponible.', 'تتبع النقل غير متاح.'),
    );
  }
}
