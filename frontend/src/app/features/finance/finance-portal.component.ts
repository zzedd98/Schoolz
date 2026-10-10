import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, switchMap } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  FamilyFinanceApiService,
  FamilyLedger,
  InstallmentPlan,
  Invoice,
  OnlinePayment,
  Receipt,
} from '../../core/finance/billing-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-finance-portal',
  imports: [CommonModule, RouterLink],
  templateUrl: './finance-portal.component.html',
  styleUrl: './finance-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinancePortalComponent {
  private readonly api = inject(FamilyFinanceApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  protected readonly i18n = inject(LocaleService);

  protected readonly ledger = signal<FamilyLedger | null>(null);
  protected readonly installmentPlans = signal<readonly InstallmentPlan[]>([]);
  protected readonly onlinePayments = signal<readonly OnlinePayment[]>([]);
  protected readonly selectedInvoice = signal<Invoice | null>(null);
  protected readonly selectedReceipt = signal<Receipt | null>(null);
  protected readonly busy = signal(false);
  protected readonly authRequired = signal(false);
  protected readonly error = signal('');
  protected readonly sandboxPayments = environment.sandboxPayments;

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected logout(): void {
    this.busy.set(true);
    this.admissions
      .logout()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.redirectToLogin(false),
        error: () => this.redirectToLogin(false),
      });
  }

  protected showReceipt(receiptId: string): void {
    this.busy.set(true);
    this.api
      .receipt(receiptId)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (receipt) => this.selectedReceipt.set(receipt),
        error: () =>
          this.error.set(this.text('Ce reçu est introuvable.', 'تعذر العثور على هذا الإيصال.')),
      });
  }

  protected printReceipt(): void {
    globalThis.print?.();
  }

  protected payableInvoice(): Invoice | null {
    return this.ledger()?.invoices.find((invoice) => invoice.balanceAmount > 0) ?? null;
  }

  protected initializeOnlinePayment(): void {
    const invoice = this.payableInvoice();
    if (!invoice || this.busy()) return;
    const schedule = this.installmentPlans()
      .flatMap((plan) => plan.schedules)
      .find((item) => item.remainingAmount > 0);
    const amount = Math.min(
      invoice.balanceAmount,
      schedule?.remainingAmount ?? invoice.balanceAmount,
    );
    const idempotencyKey =
      globalThis.crypto?.randomUUID?.() ?? `browser-${Date.now()}-${Math.random()}`;
    this.busy.set(true);
    this.error.set('');
    this.api
      .initializeOnlinePayment(
        {
          providerKey: 'sandbox',
          amount,
          invoiceId: invoice.id,
          ...(schedule ? { paymentScheduleId: schedule.id } : {}),
          returnUrl: '/finance',
        },
        idempotencyKey,
      )
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (payment) => this.onlinePayments.set([payment, ...this.onlinePayments()]),
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text(
                'Le paiement en ligne ne peut pas être initialisé.',
                'تعذر بدء الأداء عبر الإنترنت.',
              ),
          ),
      });
  }

  protected completeSandboxPayment(payment: OnlinePayment): void {
    if (this.busy() || !['PENDING', 'AUTHORIZED'].includes(payment.status)) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .completeSandboxPayment(payment.id)
      .pipe(
        switchMap(() =>
          forkJoin({
            ledger: this.api.ledger(),
            plans: this.api.installmentPlans(),
            onlinePayments: this.api.onlinePayments(),
          }),
        ),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ ledger, plans, onlinePayments }) => {
          this.ledger.set(ledger);
          this.installmentPlans.set(plans);
          this.onlinePayments.set(onlinePayments);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text('La confirmation sandbox a échoué.', 'فشل تأكيد الأداء التجريبي.'),
          ),
      });
  }

  protected statusLabel(status: string): string {
    const french: Record<string, string> = {
      DRAFT: 'Brouillon',
      ISSUED: 'À régler',
      PARTIALLY_PAID: 'Partiellement réglée',
      PARTIALLY_PAID_OVERDUE: 'Partielle · en retard',
      PAID: 'Réglée',
      UPCOMING: 'À venir',
      OVERDUE: 'En retard',
      VALIDATED: 'Validé',
      REVERSED: 'Annulé',
      VALID: 'Valide',
      PENDING: 'En attente',
      AUTHORIZED: 'Autorisé',
      FAILED: 'Échoué',
      CANCELLED: 'Annulé',
      REFUNDED: 'Remboursé',
    };
    const arabic: Record<string, string> = {
      DRAFT: 'مسودة',
      ISSUED: 'مستحقة',
      PARTIALLY_PAID: 'مؤداة جزئيا',
      PARTIALLY_PAID_OVERDUE: 'جزئية ومتأخرة',
      PAID: 'مؤداة',
      UPCOMING: 'قادمة',
      OVERDUE: 'متأخرة',
      VALIDATED: 'مؤكد',
      REVERSED: 'ملغى',
      VALID: 'صالح',
      PENDING: 'في الانتظار',
      AUTHORIZED: 'مصرح به',
      FAILED: 'فشل',
      CANCELLED: 'ملغى',
      REFUNDED: 'مسترجع',
    };
    return (this.i18n.locale() === 'ar-MA' ? arabic : french)[status] ?? status;
  }

  private load(): void {
    this.busy.set(true);
    forkJoin({
      ledger: this.api.ledger(),
      plans: this.api.installmentPlans(),
      onlinePayments: this.api.onlinePayments(),
    })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ ledger, plans, onlinePayments }) => {
          this.ledger.set(ledger);
          this.installmentPlans.set(plans);
          this.onlinePayments.set(onlinePayments);
          this.authRequired.set(false);
        },
        error: (response: { status?: number; error?: { detail?: string } }) => {
          this.authRequired.set(response.status === 401);
          if (response.status === 401) {
            this.redirectToLogin(true);
            return;
          }
          if (response.status !== 401) {
            this.error.set(
              response.error?.detail ??
                this.text('Le compte financier est indisponible.', 'الحساب المالي غير متاح.'),
            );
          }
        },
      });
  }

  private redirectToLogin(returnToPortal: boolean): void {
    this.ledger.set(null);
    this.installmentPlans.set([]);
    this.onlinePayments.set([]);
    void this.router.navigate(['/login'], {
      ...(returnToPortal ? { queryParams: { portal: 'family', returnUrl: this.router.url } } : {}),
      replaceUrl: true,
    });
  }
}
