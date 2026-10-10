import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, of, switchMap } from 'rxjs';

import {
  BillingApiService,
  CollectionsDashboard,
  FamilyLedger,
  FinanceDashboard,
  InstallmentPlanCommand,
  Invoice,
  InvoiceLineCommand,
  OnlinePayment,
  Payment,
  PaymentSchedule,
  ReminderRule,
} from '../../core/finance/billing-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

interface EditableLine {
  serviceType: string;
  label: string;
  quantity: number;
  unitAmount: number;
  discountAmount: number;
}

interface EditableInstallment {
  dueDate: string;
  amount: number;
}

@Component({
  selector: 'app-finance-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './finance-dashboard.component.html',
  styleUrl: './finance-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceDashboardComponent {
  private readonly api = inject(BillingApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly i18n = inject(LocaleService);

  protected readonly dashboard = signal<FinanceDashboard | null>(null);
  protected readonly collections = signal<CollectionsDashboard | null>(null);
  protected readonly ledger = signal<FamilyLedger | null>(null);
  protected readonly onlinePayments = signal<readonly OnlinePayment[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly invoiceFormOpen = signal(false);
  protected readonly paymentFormOpen = signal(false);
  protected readonly planFormOpen = signal(false);
  protected readonly expandedInvoiceId = signal<string | null>(null);
  protected readonly expandedPlanId = signal<string | null>(null);

  protected issueDate = this.isoDate(new Date());
  protected dueDate = this.isoDate(new Date(Date.now() + 30 * 86_400_000));
  protected lines: EditableLine[] = this.demoLines();
  protected paymentAmount = 0;
  protected paymentMethod = 'BANK_TRANSFER';
  protected paymentReference = '';
  protected reversalReason = 'Annulation contrôlée après vérification comptable';
  protected collectionsAsOf = this.isoDate(new Date());
  protected planName = 'Échéancier scolarité';
  protected planFrequency = 'MONTHLY';
  protected planTotal = 12_000;
  protected planStartsOn = this.isoDate(new Date());
  protected planInstallmentCount = 3;
  protected customInstallments: EditableInstallment[] = [
    { dueDate: this.isoDate(new Date()), amount: 6000 },
    { dueDate: this.isoDate(new Date(Date.now() + 30 * 86_400_000)), amount: 6000 },
  ];
  protected reminderChannel = 'SMS';
  protected readonly allocationAmounts: Record<string, number> = {};
  protected readonly scheduleAllocationAmounts: Record<string, number> = {};

  constructor() {
    this.load();
    this.loadCollections();
    this.loadOnlinePayments();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected load(preferredAccountId?: string): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .dashboard()
      .pipe(
        switchMap((dashboard) => {
          this.dashboard.set(dashboard);
          const accountId =
            preferredAccountId ?? this.ledger()?.account.id ?? dashboard.accounts.at(0)?.account.id;
          return accountId ? this.api.ledger(accountId) : of(null);
        }),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (ledger) => {
          this.ledger.set(ledger);
          this.prepareAllocations(ledger);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'Impossible de charger les comptes familles.'),
      });
  }

  protected selectAccount(accountId: string): void {
    if (!accountId || accountId === this.ledger()?.account.id) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .ledger(accountId)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (ledger) => {
          this.ledger.set(ledger);
          this.prepareAllocations(ledger);
          this.loadOnlinePayments(accountId);
        },
        error: () => this.error.set('Ce compte financier ne peut pas être ouvert.'),
      });
  }

  protected loadCollections(): void {
    this.api
      .collections(this.collectionsAsOf)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (dashboard) => {
          this.collections.set(dashboard);
          this.prepareScheduleAllocations(dashboard);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'Le recouvrement ne peut pas être chargé.'),
      });
  }

  protected loadOnlinePayments(accountId?: string): void {
    this.api
      .onlinePayments(accountId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (payments) => this.onlinePayments.set(payments),
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ?? 'Les paiements en ligne ne peuvent pas être chargés.',
          ),
      });
  }

  protected verifyOnlinePayment(payment: OnlinePayment): void {
    this.busy.set(true);
    this.api
      .verifyOnlinePayment(payment.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (verified) => {
          this.notice.set(
            `${verified.providerKey} · ${verified.providerPaymentId} vérifié: ${verified.status}.`,
          );
          this.loadOnlinePayments(this.ledger()?.account.id);
          this.load(this.ledger()?.account.id);
          this.loadCollections();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'La vérification fournisseur a échoué.'),
      });
  }

  protected createInstallmentPlan(): void {
    const ledger = this.ledger();
    if (!ledger || !this.planName.trim() || this.planTotal <= 0 || !this.planStartsOn) return;
    const command: InstallmentPlanCommand = {
      name: this.planName.trim(),
      frequency: this.planFrequency,
      totalAmount: this.planTotal,
      startsOn: this.planStartsOn,
      ...(this.planFrequency === 'CUSTOM'
        ? {
            installments: this.customInstallments
              .filter((item) => item.dueDate && item.amount > 0)
              .map((item) => ({ dueDate: item.dueDate, amount: item.amount })),
          }
        : { installmentCount: this.planInstallmentCount }),
    };
    this.busy.set(true);
    this.api
      .createInstallmentPlan(ledger.account.id, command)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (plan) => {
          this.notice.set(
            `Échéancier ${plan.planNumber} créé avec ${plan.schedules.length} dates.`,
          );
          this.planFormOpen.set(false);
          this.loadCollections();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? "L'échéancier n'a pas pu être créé."),
      });
  }

  protected addCustomInstallment(): void {
    this.customInstallments = [
      ...this.customInstallments,
      { dueDate: this.planStartsOn, amount: 0 },
    ];
  }

  protected removeCustomInstallment(index: number): void {
    if (this.customInstallments.length === 1) return;
    this.customInstallments = this.customInstallments.filter(
      (_, installmentIndex) => installmentIndex !== index,
    );
  }

  protected togglePlan(planId: string): void {
    this.expandedPlanId.set(this.expandedPlanId() === planId ? null : planId);
  }

  protected manualReminder(schedule: PaymentSchedule): void {
    this.busy.set(true);
    this.api
      .manualReminder(schedule.id, this.reminderChannel)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (action) => {
          this.notice.set(`Relance ${action.channel} préparée via ${action.adapterKey}.`);
          this.loadCollections();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? "La relance n'a pas pu être préparée."),
      });
  }

  protected runAutomaticReminders(): void {
    this.busy.set(true);
    this.api
      .runAutomaticReminders(this.collectionsAsOf)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (run) => {
          this.notice.set(
            `${run.createdActions} relance(s) préparée(s), ${run.skippedActions} déjà traitée(s).`,
          );
          this.loadCollections();
        },
        error: () => this.error.set("Le cycle automatique n'a pas pu être exécuté."),
      });
  }

  protected toggleRule(rule: ReminderRule, field: 'active' | 'automatic'): void {
    const updated = { ...rule, [field]: !rule[field] };
    this.api
      .updateReminderRule(updated)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loadCollections(),
        error: () => this.error.set("La règle de relance n'a pas pu être mise à jour."),
      });
  }

  protected addLine(): void {
    this.lines = [
      ...this.lines,
      { serviceType: 'OTHER_FEE', label: '', quantity: 1, unitAmount: 0, discountAmount: 0 },
    ];
  }

  protected removeLine(index: number): void {
    if (this.lines.length === 1) return;
    this.lines = this.lines.filter((_, lineIndex) => lineIndex !== index);
  }

  protected resetInvoice(): void {
    this.lines = this.demoLines();
    this.invoiceFormOpen.set(false);
  }

  protected createInvoice(): void {
    const ledger = this.ledger();
    const lines = this.lines
      .filter((line) => line.label.trim() && line.quantity > 0 && line.unitAmount >= 0)
      .map<InvoiceLineCommand>((line) => ({
        serviceType: line.serviceType,
        label: line.label.trim(),
        quantity: line.quantity,
        unitAmount: line.unitAmount,
        discountAmount: Math.max(0, line.discountAmount),
      }));
    if (!ledger || lines.length === 0) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .createInvoice(ledger.account.id, { issueDate: this.issueDate, dueDate: this.dueDate, lines })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (invoice) => {
          this.notice.set(`Facture ${invoice.invoiceNumber} créée en brouillon.`);
          this.invoiceFormOpen.set(false);
          this.load(ledger.account.id);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'La facture n’a pas pu être créée.'),
      });
  }

  protected issue(invoice: Invoice): void {
    if (invoice.status !== 'DRAFT') return;
    this.busy.set(true);
    this.api
      .issueInvoice(invoice.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (issued) => {
          this.notice.set(`Facture ${issued.invoiceNumber} émise et verrouillée.`);
          this.load(issued.familyAccountId);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'La facture n’a pas pu être émise.'),
      });
  }

  protected recordPayment(): void {
    const ledger = this.ledger();
    if (!ledger || this.paymentAmount <= 0) return;
    const allocations = ledger.invoices
      .map((invoice) => ({
        invoiceId: invoice.id,
        amount: this.allocationAmounts[invoice.id] ?? 0,
      }))
      .filter((allocation) => allocation.amount > 0);
    const scheduleAllocations =
      this.collections()
        ?.plans.filter((plan) => plan.familyAccountId === ledger.account.id)
        .flatMap((plan) => plan.schedules)
        .map((schedule) => ({
          paymentScheduleId: schedule.id,
          amount: this.scheduleAllocationAmounts[schedule.id] ?? 0,
        }))
        .filter((allocation) => allocation.amount > 0) ?? [];
    this.busy.set(true);
    this.error.set('');
    this.api
      .recordPayment(ledger.account.id, {
        amount: this.paymentAmount,
        paymentMethod: this.paymentMethod,
        externalReference: this.paymentReference.trim() || undefined,
        allocations,
        scheduleAllocations,
      })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (payment) => {
          this.notice.set(
            `Paiement ${payment.paymentNumber} validé · reçu ${payment.receiptNumber}.`,
          );
          this.paymentFormOpen.set(false);
          this.paymentAmount = 0;
          this.paymentReference = '';
          this.load(ledger.account.id);
          this.loadCollections();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'Le paiement n’a pas pu être enregistré.'),
      });
  }

  protected reverse(payment: Payment): void {
    if (payment.paymentType !== 'PAYMENT' || payment.status !== 'VALIDATED') return;
    const reason = this.reversalReason.trim();
    if (!reason) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .reversePayment(payment.id, reason)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (reversal) => {
          this.notice.set(
            `Annulation ${reversal.paymentNumber} créée sans supprimer l’encaissement.`,
          );
          this.load(payment.familyAccountId);
          this.loadCollections();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'Le paiement n’a pas pu être annulé.'),
      });
  }

  protected toggleInvoice(invoiceId: string): void {
    this.expandedInvoiceId.set(this.expandedInvoiceId() === invoiceId ? null : invoiceId);
  }

  protected allocationTotal(): number {
    return Object.values(this.allocationAmounts).reduce(
      (total, amount) => total + (amount || 0),
      0,
    );
  }

  protected scheduleAllocationTotal(): number {
    return Object.values(this.scheduleAllocationAmounts).reduce(
      (total, amount) => total + (amount || 0),
      0,
    );
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      ISSUED: 'Émise',
      PARTIALLY_PAID: 'Partiellement réglée',
      PAID: 'Réglée',
      VALIDATED: 'Validé',
      REVERSED: 'Annulé',
      UPCOMING: 'À venir',
      OVERDUE: 'En retard',
      PARTIALLY_PAID_OVERDUE: 'Partielle · en retard',
    };
    return labels[status] ?? status;
  }

  protected serviceLabel(service: string): string {
    const labels: Record<string, string> = {
      TUITION: 'Scolarité',
      REGISTRATION: 'Inscription',
      TRANSPORT: 'Transport',
      CAFETERIA: 'Cantine',
      ACTIVITY: 'Activité',
      DAYCARE: 'Garderie',
      OTHER_FEE: 'Autres frais',
    };
    return labels[service] ?? service;
  }

  private prepareAllocations(ledger: FamilyLedger | null): void {
    for (const key of Object.keys(this.allocationAmounts)) delete this.allocationAmounts[key];
    const first = ledger?.invoices.find(
      (invoice) => invoice.status !== 'DRAFT' && invoice.balanceAmount > 0,
    );
    if (first) {
      this.allocationAmounts[first.id] = first.balanceAmount;
      this.paymentAmount = first.balanceAmount;
    }
    this.prepareScheduleAllocations(this.collections());
  }

  private prepareScheduleAllocations(dashboard: CollectionsDashboard | null): void {
    for (const key of Object.keys(this.scheduleAllocationAmounts)) {
      delete this.scheduleAllocationAmounts[key];
    }
    const accountId = this.ledger()?.account.id;
    if (!dashboard || !accountId) return;
    const first = dashboard.plans
      .filter((plan) => plan.familyAccountId === accountId)
      .flatMap((plan) => plan.schedules)
      .find((schedule) => schedule.remainingAmount > 0);
    if (first) {
      this.scheduleAllocationAmounts[first.id] = Math.min(
        first.remainingAmount,
        this.paymentAmount || first.remainingAmount,
      );
    }
  }

  private demoLines(): EditableLine[] {
    return [
      {
        serviceType: 'TUITION',
        label: 'Scolarité annuelle',
        quantity: 1,
        unitAmount: 32_000,
        discountAmount: 2_000,
      },
      {
        serviceType: 'TRANSPORT',
        label: 'Transport scolaire',
        quantity: 1,
        unitAmount: 6_000,
        discountAmount: 0,
      },
      {
        serviceType: 'CAFETERIA',
        label: 'Cantine scolaire',
        quantity: 1,
        unitAmount: 3_500,
        discountAmount: 0,
      },
    ];
  }

  private isoDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }
}
