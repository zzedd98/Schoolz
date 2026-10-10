import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface FamilyAccount {
  readonly id: string;
  readonly portalAccountId: string;
  readonly accountNumber: string;
  readonly displayName: string;
  readonly email: string;
  readonly currencyCode: string;
  readonly status: string;
}

export interface AccountSummary {
  readonly account: FamilyAccount;
  readonly totalBilled: number;
  readonly totalPaid: number;
  readonly totalCredits: number;
  readonly totalRefunded: number;
  readonly totalAdjustments: number;
  readonly balance: number;
  readonly overdue: number;
}

export interface FinanceDashboard {
  readonly generatedAt: string;
  readonly currencyCode: string;
  readonly familyAccountCount: number;
  readonly totalBilled: number;
  readonly totalPaid: number;
  readonly totalOutstanding: number;
  readonly totalOverdue: number;
  readonly accounts: readonly AccountSummary[];
}

export interface InvoiceLine {
  readonly id: string;
  readonly studentId: string | null;
  readonly studentName: string | null;
  readonly serviceType: string;
  readonly label: string;
  readonly description: string | null;
  readonly quantity: number;
  readonly unitAmount: number;
  readonly grossAmount: number;
  readonly discountAmount: number;
  readonly lineTotal: number;
  readonly position: number;
}

export interface Invoice {
  readonly id: string;
  readonly familyAccountId: string;
  readonly invoiceNumber: string;
  readonly issueDate: string;
  readonly dueDate: string;
  readonly currencyCode: string;
  readonly status: string;
  readonly subtotalAmount: number;
  readonly discountAmount: number;
  readonly totalAmount: number;
  readonly paidAmount: number;
  readonly balanceAmount: number;
  readonly issuedAt: string | null;
  readonly lines: readonly InvoiceLine[];
}

export interface PaymentAllocation {
  readonly invoiceId: string;
  readonly invoiceNumber: string;
  readonly amount: number;
}

export interface Payment {
  readonly id: string;
  readonly familyAccountId: string;
  readonly paymentNumber: string;
  readonly paymentType: 'PAYMENT' | 'REVERSAL';
  readonly reversalOfPaymentId: string | null;
  readonly paymentMethod: string;
  readonly amount: number;
  readonly currencyCode: string;
  readonly externalReference: string | null;
  readonly status: string;
  readonly receivedAt: string;
  readonly reversalReason: string | null;
  readonly receiptId: string;
  readonly receiptNumber: string;
  readonly receiptStatus: string;
  readonly allocations: readonly PaymentAllocation[];
  readonly scheduleAllocations: readonly SchedulePaymentAllocation[];
}

export interface SchedulePaymentAllocation {
  readonly paymentScheduleId: string;
  readonly planNumber: string;
  readonly dueDate: string;
  readonly amount: number;
}

export interface Receipt {
  readonly id: string;
  readonly familyAccountId: string;
  readonly paymentId: string;
  readonly paymentNumber: string;
  readonly paymentMethod: string;
  readonly receiptNumber: string;
  readonly receiptType: string;
  readonly amount: number;
  readonly currencyCode: string;
  readonly issuedAt: string;
  readonly status: string;
}

export interface LedgerEntry {
  readonly id: string;
  readonly type: string;
  readonly reference: string;
  readonly label: string;
  readonly amount: number;
  readonly occurredAt: string;
  readonly status: string;
  readonly balanceAfter: number;
}

export interface Credit {
  readonly id: string;
  readonly familyAccountId: string;
  readonly creditNumber: string;
  readonly amount: number;
  readonly availableAmount: number;
  readonly currencyCode: string;
  readonly reason: string;
  readonly status: string;
  readonly issuedAt: string;
}

export interface Refund {
  readonly id: string;
  readonly familyAccountId: string;
  readonly creditId: string;
  readonly refundNumber: string;
  readonly amount: number;
  readonly currencyCode: string;
  readonly refundMethod: string;
  readonly reason: string;
  readonly status: string;
  readonly refundedAt: string;
}

export interface Adjustment {
  readonly id: string;
  readonly familyAccountId: string;
  readonly invoiceId: string | null;
  readonly adjustmentNumber: string;
  readonly direction: string;
  readonly amount: number;
  readonly currencyCode: string;
  readonly reason: string;
  readonly status: string;
  readonly postedAt: string;
}

export interface FamilyLedger {
  readonly generatedAt: string;
  readonly account: FamilyAccount;
  readonly summary: AccountSummary;
  readonly invoices: readonly Invoice[];
  readonly payments: readonly Payment[];
  readonly credits: readonly Credit[];
  readonly refunds: readonly Refund[];
  readonly adjustments: readonly Adjustment[];
  readonly receipts: readonly Receipt[];
  readonly entries: readonly LedgerEntry[];
}

export interface InvoiceLineCommand {
  readonly studentId?: string;
  readonly serviceType: string;
  readonly label: string;
  readonly description?: string;
  readonly quantity: number;
  readonly unitAmount: number;
  readonly discountAmount: number;
}

export interface PaymentCommand {
  readonly amount: number;
  readonly paymentMethod: string;
  readonly externalReference?: string;
  readonly allocations: readonly { readonly invoiceId: string; readonly amount: number }[];
  readonly scheduleAllocations?: readonly {
    readonly paymentScheduleId: string;
    readonly amount: number;
  }[];
}

export interface PaymentSchedule {
  readonly id: string;
  readonly invoiceId: string | null;
  readonly position: number;
  readonly dueDate: string;
  readonly amount: number;
  readonly paidAmount: number;
  readonly remainingAmount: number;
  readonly status: string;
  readonly daysOverdue: number;
  readonly paidAt: string | null;
}

export interface InstallmentPlan {
  readonly id: string;
  readonly familyAccountId: string;
  readonly accountNumber: string;
  readonly familyName: string;
  readonly planNumber: string;
  readonly name: string;
  readonly frequency: string;
  readonly totalAmount: number;
  readonly paidAmount: number;
  readonly remainingAmount: number;
  readonly currencyCode: string;
  readonly startsOn: string;
  readonly status: string;
  readonly nextDueDate: string | null;
  readonly schedules: readonly PaymentSchedule[];
}

export interface ReminderRule {
  readonly id: string;
  readonly name: string;
  readonly offsetDays: number;
  readonly channel: string;
  readonly automatic: boolean;
  readonly active: boolean;
  readonly templateKey: string;
}

export interface CollectionAction {
  readonly id: string;
  readonly familyAccountId: string;
  readonly accountNumber: string;
  readonly familyName: string;
  readonly paymentScheduleId: string;
  readonly planNumber: string;
  readonly dueDate: string;
  readonly actionType: string;
  readonly channel: string;
  readonly status: string;
  readonly adapterKey: string;
  readonly message: string;
  readonly plannedFor: string;
  readonly executedAt: string;
}

export interface CollectionsDashboard {
  readonly generatedAt: string;
  readonly asOf: string;
  readonly currencyCode: string;
  readonly totalExpected: number;
  readonly totalReceived: number;
  readonly collectionRate: number;
  readonly unpaid: number;
  readonly overdueOver7Days: number;
  readonly overdueOver30Days: number;
  readonly overdueOver60Days: number;
  readonly unpaidInstallments: number;
  readonly plans: readonly InstallmentPlan[];
  readonly reminderRules: readonly ReminderRule[];
  readonly recentActions: readonly CollectionAction[];
}

export interface InstallmentPlanCommand {
  readonly name: string;
  readonly frequency: string;
  readonly totalAmount: number;
  readonly startsOn: string;
  readonly invoiceId?: string;
  readonly installmentCount?: number;
  readonly installments?: readonly { readonly dueDate: string; readonly amount: number }[];
}

export interface OnlinePayment {
  readonly id: string;
  readonly tenantId: string;
  readonly familyAccountId: string;
  readonly accountNumber: string;
  readonly familyName: string;
  readonly invoiceId: string | null;
  readonly invoiceNumber: string | null;
  readonly paymentScheduleId: string | null;
  readonly planNumber: string | null;
  readonly dueDate: string | null;
  readonly providerKey: string;
  readonly providerPaymentId: string;
  readonly checkoutUrl: string;
  readonly amount: number;
  readonly currencyCode: string;
  readonly status: string;
  readonly resultingPaymentId: string | null;
  readonly expiresAt: string;
  readonly verifiedAt: string | null;
  readonly completedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class BillingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/billing';

  dashboard(): Observable<FinanceDashboard> {
    return this.http.get<FinanceDashboard>(`${this.base}/dashboard`);
  }

  ledger(accountId: string): Observable<FamilyLedger> {
    return this.http.get<FamilyLedger>(`${this.base}/accounts/${accountId}`);
  }

  createInvoice(
    accountId: string,
    command: {
      readonly issueDate: string;
      readonly dueDate: string;
      readonly lines: readonly InvoiceLineCommand[];
    },
  ): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/accounts/${accountId}/invoices`, command);
  }

  issueInvoice(invoiceId: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/invoices/${invoiceId}/issue`, {});
  }

  recordPayment(accountId: string, command: PaymentCommand): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/accounts/${accountId}/payments`, command);
  }

  reversePayment(paymentId: string, reason: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${paymentId}/reverse`, { reason });
  }

  collections(asOf?: string): Observable<CollectionsDashboard> {
    const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return this.http.get<CollectionsDashboard>(`${this.base}/collections/dashboard${query}`);
  }

  createInstallmentPlan(
    accountId: string,
    command: InstallmentPlanCommand,
  ): Observable<InstallmentPlan> {
    return this.http.post<InstallmentPlan>(
      `${this.base}/collections/accounts/${accountId}/installment-plans`,
      command,
    );
  }

  manualReminder(
    scheduleId: string,
    channel: string,
    message?: string,
  ): Observable<CollectionAction> {
    return this.http.post<CollectionAction>(
      `${this.base}/collections/payment-schedules/${scheduleId}/reminders`,
      { channel, message },
    );
  }

  runAutomaticReminders(asOf?: string): Observable<{
    readonly asOf: string;
    readonly createdActions: number;
    readonly skippedActions: number;
  }> {
    const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return this.http.post<{
      readonly asOf: string;
      readonly createdActions: number;
      readonly skippedActions: number;
    }>(`${this.base}/collections/reminders/run${query}`, {});
  }

  updateReminderRule(rule: ReminderRule): Observable<ReminderRule> {
    return this.http.put<ReminderRule>(`${this.base}/collections/reminder-rules/${rule.id}`, rule);
  }

  onlinePayments(accountId?: string): Observable<readonly OnlinePayment[]> {
    const query = accountId ? `?accountId=${encodeURIComponent(accountId)}` : '';
    return this.http.get<readonly OnlinePayment[]>(`${this.base}/online-payments${query}`);
  }

  verifyOnlinePayment(paymentId: string): Observable<OnlinePayment> {
    return this.http.post<OnlinePayment>(`${this.base}/online-payments/${paymentId}/verify`, {});
  }
}

@Injectable({ providedIn: 'root' })
export class FamilyFinanceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/public/finance';
  private readonly options = { withCredentials: true } as const;

  ledger(): Observable<FamilyLedger> {
    return this.http.get<FamilyLedger>(this.base, this.options);
  }

  receipt(receiptId: string): Observable<Receipt> {
    return this.http.get<Receipt>(`${this.base}/receipts/${receiptId}`, this.options);
  }

  installmentPlans(asOf?: string): Observable<readonly InstallmentPlan[]> {
    const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return this.http.get<readonly InstallmentPlan[]>(
      `${this.base}/installment-plans${query}`,
      this.options,
    );
  }

  onlinePayments(): Observable<readonly OnlinePayment[]> {
    return this.http.get<readonly OnlinePayment[]>(`${this.base}/online-payments`, this.options);
  }

  initializeOnlinePayment(
    command: {
      readonly providerKey: string;
      readonly amount: number;
      readonly invoiceId?: string;
      readonly paymentScheduleId?: string;
      readonly returnUrl: string;
    },
    idempotencyKey: string,
  ): Observable<OnlinePayment> {
    return this.http.post<OnlinePayment>(`${this.base}/online-payments`, command, {
      ...this.options,
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  onlinePayment(paymentId: string): Observable<OnlinePayment> {
    return this.http.get<OnlinePayment>(`${this.base}/online-payments/${paymentId}`, this.options);
  }

  completeSandboxPayment(paymentId: string): Observable<OnlinePayment> {
    return this.http.post<OnlinePayment>(
      `${this.base}/online-payments/${paymentId}/sandbox-complete`,
      {},
      this.options,
    );
  }
}
