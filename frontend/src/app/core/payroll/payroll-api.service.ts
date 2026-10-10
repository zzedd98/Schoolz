import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type PayrollComponentType =
  'BASE' | 'BONUS' | 'DEDUCTION' | 'OVERTIME' | 'ABSENCE' | 'ALLOWANCE';

export interface PayrollPeriod {
  readonly id: string;
  readonly code: string;
  readonly label: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly paymentDate: string;
  readonly currency: string;
  readonly status: string;
}

export interface SalaryComponent {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly componentType: PayrollComponentType;
  readonly calculationBasis: 'PROFILE_AMOUNT' | 'FIXED' | 'PERCENT_OF_BASE' | 'PER_UNIT';
  readonly description: string | null;
  readonly expenseAccountCode: string | null;
  readonly liabilityAccountCode: string | null;
  readonly active: boolean;
  readonly displayOrder: number;
}

export interface SalaryRule {
  readonly id: string;
  readonly componentId: string;
  readonly componentCode: string;
  readonly componentName: string;
  readonly code: string;
  readonly name: string;
  readonly priority: number;
  readonly fixedAmount: number | null;
  readonly ratePercent: number | null;
  readonly unitRate: number | null;
  readonly conditionExpression: string | null;
  readonly sourceReference: string | null;
  readonly legallyValidated: boolean;
  readonly effectiveFrom: string;
  readonly effectiveTo: string | null;
  readonly active: boolean;
}

export interface ProfileComponent {
  readonly id: string;
  readonly componentId: string;
  readonly ruleId: string | null;
  readonly componentCode: string;
  readonly componentName: string;
  readonly componentType: PayrollComponentType;
  readonly calculationBasis: string;
  readonly ruleName: string | null;
  readonly configuredAmount: number | null;
  readonly configuredRate: number | null;
  readonly configuredUnitRate: number | null;
}

export interface SalaryProfile {
  readonly id: string;
  readonly employeeId: string;
  readonly employeeName: string;
  readonly employeeNumber: string;
  readonly code: string;
  readonly currency: string;
  readonly effectiveFrom: string;
  readonly effectiveTo: string | null;
  readonly active: boolean;
  readonly paymentMethod: string | null;
  readonly notes: string | null;
  readonly components: readonly ProfileComponent[];
}

export interface PayrollRun {
  readonly id: string;
  readonly periodId: string;
  readonly periodCode: string;
  readonly periodLabel: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly paymentDate: string;
  readonly currency: string;
  readonly runNumber: string;
  readonly status: 'DRAFT' | 'CALCULATING' | 'CALCULATED' | 'FINALIZED' | 'EXPORTED';
  readonly employeeCount: number;
  readonly totalGross: number;
  readonly totalDeductions: number;
  readonly totalNet: number;
  readonly startedAt: string;
  readonly calculatedAt: string | null;
  readonly finalizedAt: string | null;
}

export interface SalaryAdjustment {
  readonly id: string;
  readonly employeeId: string;
  readonly employeeName: string;
  readonly periodId: string;
  readonly periodCode: string;
  readonly componentId: string;
  readonly componentCode: string;
  readonly componentName: string;
  readonly componentType: PayrollComponentType;
  readonly quantity: number | null;
  readonly unitRate: number | null;
  readonly amount: number | null;
  readonly reason: string;
  readonly status: 'PENDING' | 'APPLIED' | 'CANCELLED';
  readonly createdAt: string;
  readonly appliedAt: string | null;
}

export interface PayslipSummary {
  readonly id: string;
  readonly payrollRunId: string;
  readonly employeeId: string;
  readonly employeeName: string;
  readonly employeeNumber: string;
  readonly payslipNumber: string;
  readonly currency: string;
  readonly grossAmount: number;
  readonly deductionAmount: number;
  readonly netAmount: number;
  readonly status: string;
  readonly calculatedAt: string;
  readonly finalizedAt: string | null;
}

export interface PayslipLine {
  readonly id: string;
  readonly componentId: string;
  readonly ruleId: string | null;
  readonly componentCode: string;
  readonly componentName: string;
  readonly componentType: PayrollComponentType;
  readonly quantity: number | null;
  readonly rate: number | null;
  readonly amount: number;
  readonly source: string;
  readonly description: string | null;
}

export interface PayslipRecord {
  readonly payslip: PayslipSummary;
  readonly lines: readonly PayslipLine[];
  readonly complianceNotice: string;
}

export interface AccountingExport {
  readonly id: string;
  readonly exportReference: string;
  readonly format: 'CSV' | 'JSON';
  readonly content: string;
  readonly sha256: string;
  readonly createdAt: string;
  readonly complianceNotice: string;
}

export interface PayrollDashboard {
  readonly generatedAt: string;
  readonly complianceNotice: string;
  readonly periods: readonly PayrollPeriod[];
  readonly components: readonly SalaryComponent[];
  readonly rules: readonly SalaryRule[];
  readonly profiles: readonly SalaryProfile[];
  readonly runs: readonly PayrollRun[];
  readonly adjustments: readonly SalaryAdjustment[];
}

@Injectable({ providedIn: 'root' })
export class PayrollApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<PayrollDashboard>('/api/v1/payroll/dashboard');
  }

  createPeriod(command: {
    code: string;
    label: string;
    startsOn: string;
    endsOn: string;
    paymentDate: string;
    currency: string;
    status: string;
  }) {
    return this.http.post<PayrollPeriod>('/api/v1/payroll/periods', command);
  }

  createComponent(command: {
    code: string;
    name: string;
    componentType: PayrollComponentType;
    calculationBasis: SalaryComponent['calculationBasis'];
    description: string;
    expenseAccountCode: string;
    liabilityAccountCode: string;
    displayOrder: number;
  }) {
    return this.http.post<SalaryComponent>('/api/v1/payroll/components', command);
  }

  createAdjustment(command: {
    employeeId: string;
    periodId: string;
    componentId: string;
    quantity?: number;
    unitRate?: number;
    amount?: number;
    reason: string;
  }) {
    return this.http.post<SalaryAdjustment>('/api/v1/payroll/adjustments', command);
  }

  createRule(command: {
    componentId: string;
    code: string;
    name: string;
    priority: number;
    fixedAmount?: number;
    ratePercent?: number;
    unitRate?: number;
    sourceReference: string;
    legallyValidated: boolean;
    effectiveFrom: string;
  }) {
    return this.http.post<SalaryRule>('/api/v1/payroll/rules', command);
  }

  createRun(periodId: string) {
    return this.http.post<PayrollRun>('/api/v1/payroll/runs', { periodId });
  }

  calculate(runId: string) {
    return this.http.post<PayrollRun>(`/api/v1/payroll/runs/${runId}/calculate`, {});
  }

  finalize(runId: string) {
    return this.http.post<PayrollRun>(`/api/v1/payroll/runs/${runId}/finalize`, {});
  }

  payslips(runId: string) {
    return this.http.get<readonly PayslipSummary[]>(`/api/v1/payroll/runs/${runId}/payslips`);
  }

  payslip(payslipId: string) {
    return this.http.get<PayslipRecord>(`/api/v1/payroll/payslips/${payslipId}`);
  }

  accountingExport(runId: string, format: 'CSV' | 'JSON') {
    const params = new HttpParams().set('format', format);
    return this.http.post<AccountingExport>(
      `/api/v1/payroll/runs/${runId}/accounting-export`,
      {},
      { params },
    );
  }
}
