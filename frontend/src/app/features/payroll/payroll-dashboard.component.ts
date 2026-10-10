import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import {
  AccountingExport,
  PayrollApiService,
  PayrollComponentType,
  PayrollDashboard,
  PayrollRun,
  PayslipRecord,
  PayslipSummary,
  SalaryComponent,
} from '../../core/payroll/payroll-api.service';

type PayrollView = 'cycle' | 'payslips' | 'rules' | 'adjustments';
type PayrollDrawer = 'period' | 'component' | 'rule' | 'adjustment' | null;

@Component({
  selector: 'app-payroll-dashboard',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  templateUrl: './payroll-dashboard.component.html',
  styleUrl: './payroll-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollDashboardComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(PayrollApiService);

  protected readonly dashboard = signal<PayrollDashboard | null>(null);
  protected readonly activeView = signal<PayrollView>('cycle');
  protected readonly drawer = signal<PayrollDrawer>(null);
  protected readonly selectedRunId = signal<string | null>(null);
  protected readonly payslips = signal<readonly PayslipSummary[]>([]);
  protected readonly selectedPayslip = signal<PayslipRecord | null>(null);
  protected readonly lastExport = signal<AccountingExport | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  protected readonly feedback = signal<string | null>(null);

  protected periodDraft = {
    code: '2026-09',
    label: 'Paie septembre 2026',
    startsOn: '2026-09-01',
    endsOn: '2026-09-30',
    paymentDate: '2026-09-30',
    currency: 'MAD',
    status: 'OPEN',
  };
  protected componentDraft: {
    code: string;
    name: string;
    componentType: PayrollComponentType;
    calculationBasis: SalaryComponent['calculationBasis'];
    description: string;
    expenseAccountCode: string;
    liabilityAccountCode: string;
    displayOrder: number;
  } = {
    code: '',
    name: '',
    componentType: 'ALLOWANCE',
    calculationBasis: 'FIXED',
    description: '',
    expenseAccountCode: 'PAYROLL_EXPENSE',
    liabilityAccountCode: 'PAYROLL_PAYABLE',
    displayOrder: 70,
  };
  protected ruleDraft = {
    componentId: '',
    code: '',
    name: '',
    priority: 100,
    fixedAmount: 0,
    ratePercent: 0,
    unitRate: 0,
    sourceReference: '',
    effectiveFrom: '2026-09-01',
  };
  protected adjustmentDraft = {
    employeeId: '',
    periodId: '',
    componentId: '',
    quantity: 0,
    unitRate: 0,
    amount: 0,
    reason: '',
  };

  ngOnInit(): void {
    this.load();
  }

  protected load(preserveSelection = false): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
        const runId =
          preserveSelection && this.selectedRunId() ? this.selectedRunId() : dashboard.runs[0]?.id;
        if (runId) this.selectRun(runId);
        this.ruleDraft.componentId ||= dashboard.components[0]?.id ?? '';
        this.adjustmentDraft.employeeId ||= dashboard.profiles[0]?.employeeId ?? '';
        this.adjustmentDraft.periodId ||= dashboard.periods[0]?.id ?? '';
        this.adjustmentDraft.componentId ||= dashboard.components[1]?.id ?? '';
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  protected selectRun(runId: string): void {
    this.selectedRunId.set(runId);
    this.selectedPayslip.set(null);
    this.api.payslips(runId).subscribe({
      next: (payslips) => {
        this.payslips.set(payslips);
        if (payslips[0]) this.openPayslip(payslips[0].id);
      },
      error: () => this.payslips.set([]),
    });
  }

  protected openPayslip(payslipId: string): void {
    this.api.payslip(payslipId).subscribe({
      next: (record) => this.selectedPayslip.set(record),
      error: () => this.finish('Le bulletin n’a pas pu être ouvert.', false),
    });
  }

  protected selectedRun(): PayrollRun | undefined {
    return this.dashboard()?.runs.find((run) => run.id === this.selectedRunId());
  }

  protected runForPeriod(periodId: string): PayrollRun | undefined {
    return this.dashboard()?.runs.find((run) => run.periodId === periodId);
  }

  protected calculate(run: PayrollRun): void {
    this.saving.set(true);
    this.api.calculate(run.id).subscribe({
      next: () => this.finish('Calcul terminé et bulletins régénérés.'),
      error: () => this.finish('Le calcul n’a pas pu être exécuté.', false),
    });
  }

  protected finalize(run: PayrollRun): void {
    if (!window.confirm('Finaliser ce run ? Les bulletins deviendront immuables.')) return;
    this.saving.set(true);
    this.api.finalize(run.id).subscribe({
      next: () => this.finish('Run finalisé. Les bulletins sont figés.'),
      error: () => this.finish('La finalisation a été refusée.', false),
    });
  }

  protected export(run: PayrollRun, format: 'CSV' | 'JSON' = 'CSV'): void {
    this.saving.set(true);
    this.api.accountingExport(run.id, format).subscribe({
      next: (result) => {
        this.lastExport.set(result);
        const blob = new Blob([result.content], {
          type: format === 'CSV' ? 'text/csv;charset=utf-8' : 'application/json',
        });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `${result.exportReference.toLowerCase()}.${format.toLowerCase()}`;
        link.click();
        URL.revokeObjectURL(link.href);
        this.finish(`Export ${format} généré et vérifié par empreinte SHA-256.`);
      },
      error: () => this.finish('Le run doit être finalisé avant export.', false),
    });
  }

  protected createPeriod(): void {
    this.saving.set(true);
    this.api.createPeriod(this.periodDraft).subscribe({
      next: () => {
        this.drawer.set(null);
        this.finish('Nouvelle période ouverte.');
      },
      error: () => this.finish('La période n’a pas pu être créée.', false),
    });
  }

  protected createComponent(): void {
    if (!this.componentDraft.code || !this.componentDraft.name) return;
    this.saving.set(true);
    this.api.createComponent(this.componentDraft).subscribe({
      next: () => {
        this.drawer.set(null);
        this.finish('Composant salarial créé.');
      },
      error: () => this.finish('Le composant n’a pas pu être créé.', false),
    });
  }

  protected createRule(): void {
    const component = this.componentById(this.ruleDraft.componentId);
    if (!component || !this.ruleDraft.code || !this.ruleDraft.name) return;
    const command = {
      componentId: component.id,
      code: this.ruleDraft.code,
      name: this.ruleDraft.name,
      priority: this.ruleDraft.priority,
      fixedAmount: component.calculationBasis === 'FIXED' ? this.ruleDraft.fixedAmount : undefined,
      ratePercent:
        component.calculationBasis === 'PERCENT_OF_BASE' ? this.ruleDraft.ratePercent : undefined,
      unitRate: component.calculationBasis === 'PER_UNIT' ? this.ruleDraft.unitRate : undefined,
      sourceReference: this.ruleDraft.sourceReference,
      legallyValidated: false,
      effectiveFrom: this.ruleDraft.effectiveFrom,
    };
    this.saving.set(true);
    this.api.createRule(command).subscribe({
      next: () => {
        this.drawer.set(null);
        this.activeView.set('rules');
        this.finish('Règle créée au statut « à valider ».');
      },
      error: () => this.finish('La règle n’a pas pu être créée.', false),
    });
  }

  protected createAdjustment(): void {
    if (
      !this.adjustmentDraft.employeeId ||
      !this.adjustmentDraft.periodId ||
      !this.adjustmentDraft.componentId ||
      !this.adjustmentDraft.reason
    )
      return;
    const command = {
      employeeId: this.adjustmentDraft.employeeId,
      periodId: this.adjustmentDraft.periodId,
      componentId: this.adjustmentDraft.componentId,
      amount: this.adjustmentDraft.amount || undefined,
      quantity: this.adjustmentDraft.quantity || undefined,
      unitRate: this.adjustmentDraft.unitRate || undefined,
      reason: this.adjustmentDraft.reason,
    };
    this.saving.set(true);
    this.api.createAdjustment(command).subscribe({
      next: () => {
        this.drawer.set(null);
        this.activeView.set('adjustments');
        this.finish('Ajustement ajouté au prochain calcul.');
      },
      error: () => this.finish('L’ajustement n’a pas pu être ajouté.', false),
    });
  }

  protected createRun(periodId: string): void {
    this.saving.set(true);
    this.api.createRun(periodId).subscribe({
      next: (run) => {
        this.selectedRunId.set(run.id);
        this.finish('Run de paie préparé. Vous pouvez maintenant le calculer.');
      },
      error: () => this.finish('Un run existe déjà pour cette période.', false),
    });
  }

  protected componentById(componentId: string): SalaryComponent | undefined {
    return this.dashboard()?.components.find((component) => component.id === componentId);
  }

  protected adjustmentValue(adjustment: PayrollDashboard['adjustments'][number]): number {
    return adjustment.amount ?? (adjustment.quantity ?? 0) * (adjustment.unitRate ?? 0);
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      OPEN: 'Ouverte',
      CALCULATING: 'Calcul en cours',
      CALCULATED: 'Calculée',
      FINALIZED: 'Finalisée',
      EXPORTED: 'Exportée',
      PENDING: 'À appliquer',
      APPLIED: 'Appliqué',
      CANCELLED: 'Annulé',
    };
    return labels[status] ?? status;
  }

  protected typeLabel(type: PayrollComponentType): string {
    const labels: Record<PayrollComponentType, string> = {
      BASE: 'Base',
      BONUS: 'Prime',
      DEDUCTION: 'Retenue',
      OVERTIME: 'Heures sup.',
      ABSENCE: 'Absence',
      ALLOWANCE: 'Indemnité',
    };
    return labels[type];
  }

  protected basisLabel(basis: SalaryComponent['calculationBasis']): string {
    return {
      PROFILE_AMOUNT: 'Montant du profil',
      FIXED: 'Montant fixe',
      PERCENT_OF_BASE: '% de la base',
      PER_UNIT: 'Par unité',
    }[basis];
  }

  private finish(message: string, reload = true): void {
    this.saving.set(false);
    this.feedback.set(message);
    if (reload) this.load(true);
    window.setTimeout(() => this.feedback.set(null), 3600);
  }
}
