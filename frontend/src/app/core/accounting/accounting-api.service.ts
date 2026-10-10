import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface AccountingConfiguration {
  readonly enabled: boolean;
  readonly baseCurrency: string;
  readonly familyLedgerAutoPost: boolean;
}

export interface ChartAccount {
  readonly id: string;
  readonly parentAccountId: string | null;
  readonly code: string;
  readonly name: string;
  readonly accountType: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
  readonly description: string | null;
  readonly allowPosting: boolean;
  readonly active: boolean;
}

export interface AccountingJournal {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly journalType: string;
  readonly active: boolean;
}

export interface FiscalPeriod {
  readonly id: string;
  readonly code: string;
  readonly label: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly status: 'OPEN' | 'CLOSED';
  readonly closedAt: string | null;
}

export interface CostCenter {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly active: boolean;
}

export interface JournalLine {
  readonly id: string;
  readonly accountId: string;
  readonly accountCode: string;
  readonly accountName: string;
  readonly costCenterId: string | null;
  readonly costCenterCode: string | null;
  readonly position: number;
  readonly label: string;
  readonly debitAmount: number;
  readonly creditAmount: number;
}

export interface JournalEntry {
  readonly id: string;
  readonly journalId: string;
  readonly journalCode: string;
  readonly fiscalPeriodId: string;
  readonly reversalOfEntryId: string | null;
  readonly entryNumber: string;
  readonly entryDate: string;
  readonly reference: string;
  readonly description: string;
  readonly sourceModule: string;
  readonly sourceType: string;
  readonly sourceId: string;
  readonly currency: string;
  readonly totalDebit: number;
  readonly totalCredit: number;
  readonly status: 'POSTED' | 'REVERSED';
  readonly postedAt: string;
  readonly lines: readonly JournalLine[];
}

export interface Supplier {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly taxIdentifier: string | null;
  readonly email: string | null;
  readonly phone: string | null;
  readonly address: string | null;
  readonly status: string;
}

export interface SupplierInvoice {
  readonly id: string;
  readonly supplierId: string;
  readonly supplierName: string;
  readonly journalId: string;
  readonly expenseAccountId: string;
  readonly payableAccountId: string;
  readonly costCenterId: string | null;
  readonly journalEntryId: string | null;
  readonly invoiceNumber: string;
  readonly invoiceDate: string;
  readonly dueDate: string;
  readonly description: string;
  readonly totalAmount: number;
  readonly currency: string;
  readonly status: string;
  readonly createdAt: string;
  readonly postedAt: string | null;
}

export interface Expense {
  readonly id: string;
  readonly supplierId: string | null;
  readonly supplierName: string | null;
  readonly journalId: string;
  readonly expenseAccountId: string;
  readonly paymentAccountId: string;
  readonly costCenterId: string | null;
  readonly journalEntryId: string | null;
  readonly expenseReference: string;
  readonly expenseDate: string;
  readonly description: string;
  readonly amount: number;
  readonly currency: string;
  readonly paymentMethod: string;
  readonly status: string;
  readonly createdAt: string;
  readonly postedAt: string | null;
}

export interface BankAccount {
  readonly id: string;
  readonly ledgerAccountId: string;
  readonly ledgerAccountCode: string;
  readonly journalId: string;
  readonly code: string;
  readonly bankName: string;
  readonly accountLabel: string;
  readonly ibanMasked: string | null;
  readonly currency: string;
  readonly active: boolean;
}

export interface Reconciliation {
  readonly id: string;
  readonly bankAccountId: string;
  readonly bankCode: string;
  readonly statementReference: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly openingBalance: number;
  readonly closingBalance: number;
  readonly matchedAmount: number;
  readonly status: string;
  readonly itemCount: number;
  readonly completedAt: string | null;
  readonly createdAt: string;
}

export interface TrialBalanceLine {
  readonly accountId: string;
  readonly accountCode: string;
  readonly accountName: string;
  readonly accountType: string;
  readonly totalDebit: number;
  readonly totalCredit: number;
  readonly balance: number;
}

export interface AccountingDashboard {
  readonly generatedAt: string;
  readonly configuration: AccountingConfiguration;
  readonly totalDebit: number;
  readonly totalCredit: number;
  readonly balanced: boolean;
  readonly familyLedgerEntryCount: number;
  readonly accounts: readonly ChartAccount[];
  readonly journals: readonly AccountingJournal[];
  readonly fiscalPeriods: readonly FiscalPeriod[];
  readonly costCenters: readonly CostCenter[];
  readonly entries: readonly JournalEntry[];
  readonly suppliers: readonly Supplier[];
  readonly supplierInvoices: readonly SupplierInvoice[];
  readonly expenses: readonly Expense[];
  readonly bankAccounts: readonly BankAccount[];
  readonly reconciliations: readonly Reconciliation[];
  readonly trialBalance: readonly TrialBalanceLine[];
}

@Injectable({ providedIn: 'root' })
export class AccountingApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<AccountingDashboard>('/api/v1/accounting/dashboard');
  }

  syncFamilyLedger() {
    return this.http.post<{ postedEntries: number; totalFamilyLedgerEntries: number }>(
      '/api/v1/accounting/family-ledger/sync',
      {},
    );
  }

  createSupplierInvoice(command: object) {
    return this.http.post<SupplierInvoice>('/api/v1/accounting/supplier-invoices', command);
  }

  postSupplierInvoice(invoiceId: string) {
    return this.http.post<SupplierInvoice>(
      `/api/v1/accounting/supplier-invoices/${invoiceId}/post`,
      {},
    );
  }

  createExpense(command: object) {
    return this.http.post<Expense>('/api/v1/accounting/expenses', command);
  }

  postExpense(expenseId: string) {
    return this.http.post<Expense>(`/api/v1/accounting/expenses/${expenseId}/post`, {});
  }

  createAccount(command: object) {
    return this.http.post<ChartAccount>('/api/v1/accounting/accounts', command);
  }

  createReconciliation(command: object) {
    return this.http.post<Reconciliation>('/api/v1/accounting/reconciliations', command);
  }

  reverseEntry(entryId: string, reason: string) {
    return this.http.post<JournalEntry>(`/api/v1/accounting/entries/${entryId}/reverse`, {
      reason,
    });
  }
}
