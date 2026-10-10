import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import {
  InvoiceMatch,
  ProcurementApiService,
  ProcurementDashboard,
  PurchaseOrder,
  PurchaseRequest,
} from '../../core/procurement/procurement-api.service';

type ProcurementSection = 'requests' | 'orders' | 'invoices' | 'directory';
type Drawer =
  'request' | 'order' | 'receipt' | 'invoice' | 'payment' | 'supplier' | 'expense' | null;

@Component({
  selector: 'app-procurement-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './procurement-dashboard.component.html',
  styleUrl: './procurement-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcurementDashboardComponent {
  private readonly api = inject(ProcurementApiService);

  protected readonly data = signal<ProcurementDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeSection = signal<ProcurementSection>('requests');
  protected readonly drawer = signal<Drawer>(null);
  protected readonly selectedRequest = signal<PurchaseRequest | null>(null);
  protected readonly selectedOrder = signal<PurchaseOrder | null>(null);
  protected readonly selectedMatch = signal<InvoiceMatch | null>(null);

  protected requestForm = this.emptyRequest();
  protected orderForm = {
    supplierId: '',
    orderedOn: '2026-08-10',
    expectedOn: '2026-08-20',
    deliveryAddress: 'Campus Casablanca Centre — accueil marchandises',
    notes: '',
  };
  protected receiptForm = {
    receivedOn: '2026-08-10',
    deliveryNote: '',
    note: '',
    quantities: {} as Record<string, number>,
  };
  protected invoiceForm = {
    journalId: '',
    expenseAccountId: '',
    payableAccountId: '',
    costCenterId: '',
    invoiceNumber: '',
    invoiceDate: '2026-08-10',
    dueDate: '2026-09-09',
    description: '',
    totalAmount: 0,
  };
  protected paymentForm = {
    journalId: '',
    payableAccountId: '',
    paymentAccountId: '',
    paidOn: '2026-08-10',
    paymentReference: '',
    amount: 0,
    paymentMethod: 'BANK_TRANSFER',
  };
  protected supplierForm = {
    code: '',
    name: '',
    taxIdentifier: '',
    email: '',
    phone: '',
    address: '',
  };
  protected expenseForm = {
    supplierId: '',
    journalId: '',
    expenseAccountId: '',
    paymentAccountId: '',
    costCenterId: '',
    expenseReference: '',
    expenseDate: '2026-08-10',
    description: '',
    amount: 0,
    currency: 'MAD',
    paymentMethod: 'CASH',
  };

  constructor() {
    this.load();
  }

  protected selectSection(section: ProcurementSection): void {
    this.activeSection.set(section);
  }

  protected openRequest(): void {
    this.requestForm = this.emptyRequest();
    this.open('request');
  }

  protected openOrder(request: PurchaseRequest): void {
    const data = this.data();
    this.selectedRequest.set(request);
    this.orderForm = {
      supplierId: data?.accounting.suppliers[0]?.id ?? '',
      orderedOn: '2026-08-10',
      expectedOn: '2026-08-20',
      deliveryAddress: 'Campus Casablanca Centre — accueil marchandises',
      notes: '',
    };
    this.open('order');
  }

  protected openReceipt(order: PurchaseOrder): void {
    this.selectedOrder.set(order);
    this.receiptForm = {
      receivedOn: '2026-08-10',
      deliveryNote: '',
      note: '',
      quantities: Object.fromEntries(
        order.lines.map((line) => [line.id, line.quantityOrdered - line.quantityReceived]),
      ),
    };
    this.open('receipt');
  }

  protected openInvoice(order: PurchaseOrder): void {
    const data = this.data();
    this.selectedOrder.set(order);
    this.invoiceForm = {
      journalId: this.journal('PURCHASE'),
      expenseAccountId: this.account('EXPENSE'),
      payableAccountId: this.account('LIABILITY'),
      costCenterId: data?.accounting.costCenters[0]?.id ?? '',
      invoiceNumber: '',
      invoiceDate: '2026-08-10',
      dueDate: '2026-09-09',
      description: order.notes ?? `Facture ${order.orderNumber}`,
      totalAmount: order.totalAmount,
    };
    this.open('invoice');
  }

  protected openPayment(match: InvoiceMatch): void {
    this.selectedMatch.set(match);
    this.paymentForm = {
      journalId: this.journal('BANK'),
      payableAccountId: this.account('LIABILITY'),
      paymentAccountId: this.account('ASSET'),
      paidOn: '2026-08-10',
      paymentReference: '',
      amount: match.matchedAmount - match.paidAmount,
      paymentMethod: 'BANK_TRANSFER',
    };
    this.open('payment');
  }

  protected openSupplier(): void {
    this.supplierForm = {
      code: '',
      name: '',
      taxIdentifier: '',
      email: '',
      phone: '',
      address: '',
    };
    this.open('supplier');
  }

  protected openExpense(): void {
    const data = this.data();
    this.expenseForm = {
      supplierId: data?.accounting.suppliers[0]?.id ?? '',
      journalId: this.journal('CASH') || this.journal('BANK'),
      expenseAccountId: this.account('EXPENSE'),
      paymentAccountId: this.account('ASSET'),
      costCenterId: data?.accounting.costCenters[0]?.id ?? '',
      expenseReference: '',
      expenseDate: '2026-08-10',
      description: '',
      amount: 0,
      currency: data?.configuration.baseCurrency ?? 'MAD',
      paymentMethod: 'CASH',
    };
    this.open('expense');
  }

  protected closeDrawer(): void {
    this.drawer.set(null);
    this.selectedRequest.set(null);
    this.selectedOrder.set(null);
    this.selectedMatch.set(null);
  }

  protected submitRequest(request: PurchaseRequest): void {
    this.mutate(
      this.api.submitRequest(request.id),
      'La demande est envoyée dans le circuit d’approbation.',
      'La demande ne peut pas être soumise.',
    );
  }

  protected decide(request: PurchaseRequest, decision: 'APPROVED' | 'REJECTED'): void {
    const note = window.prompt(
      decision === 'APPROVED' ? 'Note d’approbation' : 'Motif du rejet',
      decision === 'APPROVED' ? 'Budget et besoin contrôlés.' : '',
    );
    if (note === null) return;
    this.mutate(
      this.api.decideRequest(request.id, decision, note),
      decision === 'APPROVED'
        ? 'L’approbation a été enregistrée.'
        : 'La demande a été rejetée et conservée dans l’historique.',
      'Cette décision ne peut pas être enregistrée.',
    );
  }

  protected saveRequest(): void {
    const form = this.requestForm;
    this.mutate(
      this.api.createRequest({
        costCenterId: form.costCenterId || null,
        requestedOn: form.requestedOn,
        title: form.title,
        businessNeed: form.businessNeed,
        urgency: form.urgency,
        lines: [
          {
            description: form.description,
            quantity: form.quantity,
            unit: form.unit,
            estimatedUnitPrice: form.estimatedUnitPrice,
          },
        ],
      }),
      'La demande d’achat est enregistrée en brouillon.',
      'La demande d’achat est incomplète.',
      true,
    );
  }

  protected saveOrder(): void {
    const request = this.selectedRequest();
    if (!request) return;
    this.mutate(
      this.api.createOrder(request.id, this.orderForm),
      'Le bon de commande est émis et rattaché à la demande approuvée.',
      'Le bon de commande ne peut pas être émis.',
      true,
    );
  }

  protected saveReceipt(): void {
    const order = this.selectedOrder();
    if (!order) return;
    this.mutate(
      this.api.receiveGoods(order.id, {
        receivedOn: this.receiptForm.receivedOn,
        deliveryNote: this.receiptForm.deliveryNote,
        note: this.receiptForm.note,
        lines: order.lines
          .filter((line) => (this.receiptForm.quantities[line.id] ?? 0) > 0)
          .map((line) => ({
            orderLineId: line.id,
            quantityReceived: this.receiptForm.quantities[line.id],
          })),
      }),
      'La réception est confirmée et les quantités sont mises à jour.',
      'La réception dépasse les quantités restantes ou est incomplète.',
      true,
    );
  }

  protected saveInvoice(): void {
    const order = this.selectedOrder();
    if (!order) return;
    this.mutate(
      this.api.recordInvoice(order.id, {
        ...this.invoiceForm,
        costCenterId: this.invoiceForm.costCenterId || null,
      }),
      'La facture est rapprochée et comptabilisée en partie double.',
      'La facture ne correspond pas à la commande entièrement reçue.',
      true,
    );
  }

  protected savePayment(): void {
    const match = this.selectedMatch();
    if (!match) return;
    this.mutate(
      this.api.payInvoice(match.id, this.paymentForm),
      'Le paiement fournisseur est enregistré et la dette comptable est soldée.',
      'Le paiement dépasse le solde ou ses comptes sont invalides.',
      true,
    );
  }

  protected saveSupplier(): void {
    this.mutate(
      this.api.createSupplier(this.supplierForm),
      'Le fournisseur est disponible dans Achats et Comptabilité.',
      'Le fournisseur est incomplet ou son code existe déjà.',
      true,
    );
  }

  protected saveExpense(): void {
    this.mutate(
      this.api.recordExpense({
        ...this.expenseForm,
        supplierId: this.expenseForm.supplierId || null,
        costCenterId: this.expenseForm.costCenterId || null,
      }),
      'La dépense directe est comptabilisée sans créer de double fournisseur.',
      'La dépense directe ne peut pas être comptabilisée.',
      true,
    );
  }

  protected requestProgress(request: PurchaseRequest): number {
    if (request.status === 'DRAFT') return 8;
    if (request.status === 'SUBMITTED') {
      return Math.max(20, (request.approvals.length / Math.max(request.requiredApprovals, 1)) * 32);
    }
    const values: Record<string, number> = {
      APPROVED: 40,
      ORDERED: 56,
      RECEIVED: 72,
      INVOICED: 86,
      PAID: 100,
      REJECTED: 100,
    };
    return values[request.status] ?? 0;
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      SUBMITTED: 'À valider',
      APPROVED: 'Approuvée',
      REJECTED: 'Rejetée',
      ORDERED: 'Commandée',
      ISSUED: 'Émise',
      PARTIALLY_RECEIVED: 'Réception partielle',
      RECEIVED: 'Réceptionnée',
      INVOICED: 'Facturée',
      MATCHED: 'Rapprochée',
      PAID: 'Payée',
      RECORDED: 'Enregistré',
      POSTED: 'Comptabilisée',
    };
    return labels[status] ?? status;
  }

  protected remaining(line: PurchaseOrder['lines'][number]): number {
    return line.quantityOrdered - line.quantityReceived;
  }

  private open(kind: Exclude<Drawer, null>): void {
    this.drawer.set(kind);
    this.error.set('');
  }

  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Le cockpit Achats ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  private mutate(
    request: Observable<unknown>,
    success: string,
    failure: string,
    close = false,
  ): void {
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.notice.set(success);
        this.error.set('');
        this.saving.set(false);
        if (close) this.closeDrawer();
        this.load(false);
      },
      error: () => {
        this.error.set(failure);
        this.saving.set(false);
      },
    });
  }

  private account(type: string): string {
    return this.data()?.accounting.accounts.find((item) => item.accountType === type)?.id ?? '';
  }

  private journal(type: string): string {
    return this.data()?.accounting.journals.find((item) => item.journalType === type)?.id ?? '';
  }

  private emptyRequest() {
    return {
      costCenterId: '',
      requestedOn: '2026-08-10',
      title: '',
      businessNeed: '',
      urgency: 'NORMAL' as 'NORMAL' | 'HIGH' | 'CRITICAL',
      description: '',
      quantity: 1,
      unit: 'unité',
      estimatedUnitPrice: 0,
    };
  }
}
