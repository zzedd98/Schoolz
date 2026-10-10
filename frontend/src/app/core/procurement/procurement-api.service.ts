import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  AccountingJournal,
  ChartAccount,
  CostCenter,
  Expense,
  Supplier,
  SupplierInvoice,
} from '../accounting/accounting-api.service';

export interface ProcurementConfiguration {
  readonly baseCurrency: string;
  readonly allowPartialReceipts: boolean;
}

export interface ApprovalThreshold {
  readonly id: string;
  readonly code: string;
  readonly label: string;
  readonly minAmount: number;
  readonly maxAmount: number | null;
  readonly requiredApprovals: number;
  readonly active: boolean;
}

export interface PurchaseRequestLine {
  readonly id: string;
  readonly position: number;
  readonly description: string;
  readonly quantity: number;
  readonly unit: string;
  readonly estimatedUnitPrice: number;
  readonly estimatedTotal: number;
}

export interface RequestApproval {
  readonly id: string;
  readonly approverId: string;
  readonly approverName: string;
  readonly decision: 'APPROVED' | 'REJECTED';
  readonly note: string | null;
  readonly decidedAt: string;
}

export interface PurchaseRequest {
  readonly id: string;
  readonly requesterId: string;
  readonly requesterName: string;
  readonly requestNumber: string;
  readonly requestedOn: string;
  readonly title: string;
  readonly businessNeed: string;
  readonly urgency: 'NORMAL' | 'HIGH' | 'CRITICAL';
  readonly estimatedAmount: number;
  readonly currency: string;
  readonly status: string;
  readonly thresholdId: string | null;
  readonly thresholdLabel: string | null;
  readonly requiredApprovals: number;
  readonly costCenterId: string | null;
  readonly costCenterCode: string | null;
  readonly submittedAt: string | null;
  readonly approvedAt: string | null;
  readonly rejectedAt: string | null;
  readonly lines: readonly PurchaseRequestLine[];
  readonly approvals: readonly RequestApproval[];
}

export interface PurchaseOrderLine {
  readonly id: string;
  readonly requestLineId: string;
  readonly position: number;
  readonly description: string;
  readonly quantityOrdered: number;
  readonly quantityReceived: number;
  readonly unit: string;
  readonly unitPrice: number;
  readonly lineTotal: number;
}

export interface PurchaseOrder {
  readonly id: string;
  readonly requestId: string;
  readonly supplierId: string;
  readonly supplierCode: string;
  readonly supplierName: string;
  readonly orderNumber: string;
  readonly orderedOn: string;
  readonly expectedOn: string | null;
  readonly deliveryAddress: string | null;
  readonly notes: string | null;
  readonly totalAmount: number;
  readonly currency: string;
  readonly status: string;
  readonly issuedAt: string;
  readonly lines: readonly PurchaseOrderLine[];
}

export interface GoodsReceiptLine {
  readonly orderLineId: string;
  readonly description: string;
  readonly quantityReceived: number;
  readonly unit: string;
}

export interface GoodsReceipt {
  readonly id: string;
  readonly orderId: string;
  readonly receiptNumber: string;
  readonly receivedOn: string;
  readonly deliveryNote: string | null;
  readonly note: string | null;
  readonly receivedById: string;
  readonly receivedByName: string;
  readonly status: string;
  readonly confirmedAt: string;
  readonly lines: readonly GoodsReceiptLine[];
}

export interface InvoiceMatch {
  readonly id: string;
  readonly orderId: string;
  readonly supplierInvoiceId: string;
  readonly invoiceNumber: string;
  readonly matchedAmount: number;
  readonly varianceAmount: number;
  readonly status: string;
  readonly paidAmount: number;
  readonly matchedAt: string;
}

export interface SupplierPayment {
  readonly id: string;
  readonly invoiceMatchId: string;
  readonly supplierId: string;
  readonly supplierName: string;
  readonly journalEntryId: string;
  readonly entryNumber: string;
  readonly paymentReference: string;
  readonly paidOn: string;
  readonly amount: number;
  readonly currency: string;
  readonly paymentMethod: string;
  readonly status: string;
  readonly recordedAt: string;
}

export interface ProcurementDashboard {
  readonly generatedAt: string;
  readonly configuration: ProcurementConfiguration;
  readonly statistics: {
    readonly awaitingApproval: number;
    readonly awaitingReceipt: number;
    readonly committedAmount: number;
    readonly paidAmount: number;
    readonly matchingExceptions: number;
  };
  readonly accounting: {
    readonly suppliers: readonly Supplier[];
    readonly journals: readonly AccountingJournal[];
    readonly accounts: readonly ChartAccount[];
    readonly costCenters: readonly CostCenter[];
    readonly supplierInvoices: readonly SupplierInvoice[];
    readonly expenses: readonly Expense[];
  };
  readonly thresholds: readonly ApprovalThreshold[];
  readonly requests: readonly PurchaseRequest[];
  readonly orders: readonly PurchaseOrder[];
  readonly receipts: readonly GoodsReceipt[];
  readonly invoiceMatches: readonly InvoiceMatch[];
  readonly payments: readonly SupplierPayment[];
}

@Injectable({ providedIn: 'root' })
export class ProcurementApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<ProcurementDashboard>('/api/v1/procurement/dashboard');
  }

  createRequest(command: object) {
    return this.http.post<PurchaseRequest>('/api/v1/procurement/requests', command);
  }

  submitRequest(requestId: string) {
    return this.http.post<PurchaseRequest>(`/api/v1/procurement/requests/${requestId}/submit`, {});
  }

  decideRequest(requestId: string, decision: 'APPROVED' | 'REJECTED', note: string) {
    return this.http.post<PurchaseRequest>(`/api/v1/procurement/requests/${requestId}/decision`, {
      decision,
      note,
    });
  }

  createOrder(requestId: string, command: object) {
    return this.http.post<PurchaseOrder>(
      `/api/v1/procurement/requests/${requestId}/orders`,
      command,
    );
  }

  receiveGoods(orderId: string, command: object) {
    return this.http.post<GoodsReceipt>(`/api/v1/procurement/orders/${orderId}/receipts`, command);
  }

  recordInvoice(orderId: string, command: object) {
    return this.http.post<InvoiceMatch>(`/api/v1/procurement/orders/${orderId}/invoice`, command);
  }

  payInvoice(invoiceMatchId: string, command: object) {
    return this.http.post<SupplierPayment>(
      `/api/v1/procurement/invoice-matches/${invoiceMatchId}/payments`,
      command,
    );
  }

  createSupplier(command: object) {
    return this.http.post<Supplier>('/api/v1/procurement/suppliers', command);
  }

  recordExpense(command: object) {
    return this.http.post<Expense>('/api/v1/procurement/expenses', command);
  }
}
