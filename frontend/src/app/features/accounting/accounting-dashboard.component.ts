import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  AccountingApiService,
  AccountingDashboard,
  ChartAccount,
  JournalEntry,
} from '../../core/accounting/accounting-api.service';

type AccountingSection = 'ledger' | 'balance' | 'suppliers' | 'bank';
type Drawer = 'supplierInvoice' | 'expense' | 'account' | 'reconciliation' | null;

interface SupplierInvoiceForm {
  supplierId: string;
  journalId: string;
  expenseAccountId: string;
  payableAccountId: string;
  costCenterId: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  description: string;
  totalAmount: number;
  currency: string;
}

interface ExpenseForm {
  supplierId: string;
  journalId: string;
  expenseAccountId: string;
  paymentAccountId: string;
  costCenterId: string;
  expenseReference: string;
  expenseDate: string;
  description: string;
  amount: number;
  currency: string;
  paymentMethod: string;
}

interface AccountForm {
  code: string;
  name: string;
  accountType: ChartAccount['accountType'];
  description: string;
  allowPosting: boolean;
}

interface ReconciliationForm {
  bankAccountId: string;
  statementReference: string;
  startsOn: string;
  endsOn: string;
  openingBalance: number;
  closingBalance: number;
}

@Component({
  selector: 'app-accounting-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './accounting-dashboard.component.html',
  styleUrl: './accounting-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountingDashboardComponent {
  private readonly api = inject(AccountingApiService);

  protected readonly data = signal<AccountingDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeSection = signal<AccountingSection>('ledger');
  protected readonly selectedEntryId = signal<string | null>(null);
  protected readonly drawer = signal<Drawer>(null);
  protected readonly saving = signal(false);

  protected supplierInvoiceForm: SupplierInvoiceForm = this.emptySupplierInvoice();
  protected expenseForm: ExpenseForm = this.emptyExpense();
  protected accountForm: AccountForm = {
    code: '',
    name: '',
    accountType: 'EXPENSE',
    description: '',
    allowPosting: true,
  };
  protected reconciliationForm: ReconciliationForm = {
    bankAccountId: '',
    statementReference: '',
    startsOn: '2026-09-01',
    endsOn: '2026-09-30',
    openingBalance: 0,
    closingBalance: 0,
  };

  constructor() {
    this.load();
  }

  protected selectSection(section: AccountingSection): void {
    this.activeSection.set(section);
    this.selectedEntryId.set(null);
  }

  protected toggleEntry(entry: JournalEntry): void {
    this.selectedEntryId.update((current) => (current === entry.id ? null : entry.id));
  }

  protected openDrawer(kind: Exclude<Drawer, null>): void {
    const dashboard = this.data();
    if (kind === 'supplierInvoice') {
      this.supplierInvoiceForm = this.emptySupplierInvoice(dashboard);
    } else if (kind === 'expense') {
      this.expenseForm = this.emptyExpense(dashboard);
    } else if (kind === 'reconciliation') {
      this.reconciliationForm = {
        ...this.reconciliationForm,
        bankAccountId: dashboard?.bankAccounts[0]?.id ?? '',
      };
    }
    this.drawer.set(kind);
    this.error.set('');
  }

  protected closeDrawer(): void {
    this.drawer.set(null);
  }

  protected syncFamilyLedger(): void {
    this.saving.set(true);
    this.api.syncFamilyLedger().subscribe({
      next: (result) => {
        this.notice.set(
          result.postedEntries === 0
            ? 'Le Family Ledger est déjà entièrement synchronisé.'
            : `${result.postedEntries} écriture(s) comptable(s) ajoutée(s).`,
        );
        this.saving.set(false);
        this.load(false);
      },
      error: () => {
        this.error.set('La synchronisation du Family Ledger a échoué.');
        this.saving.set(false);
      },
    });
  }

  protected postSupplierInvoice(invoiceId: string): void {
    this.saving.set(true);
    this.api.postSupplierInvoice(invoiceId).subscribe({
      next: () => this.afterMutation('Facture fournisseur comptabilisée en partie double.'),
      error: () => this.mutationFailed('La facture ne peut pas être comptabilisée.'),
    });
  }

  protected postExpense(expenseId: string): void {
    this.saving.set(true);
    this.api.postExpense(expenseId).subscribe({
      next: () => this.afterMutation('Dépense comptabilisée et ajoutée au Grand Livre.'),
      error: () => this.mutationFailed('La dépense ne peut pas être comptabilisée.'),
    });
  }

  protected reverseEntry(entry: JournalEntry): void {
    const reason = window.prompt(`Motif de l'extourne de ${entry.entryNumber}`);
    if (!reason?.trim()) return;
    this.saving.set(true);
    this.api.reverseEntry(entry.id, reason.trim()).subscribe({
      next: () => this.afterMutation('L’écriture inverse a été créée sans effacer l’originale.'),
      error: () => this.mutationFailed('Cette écriture ne peut pas être extournée.'),
    });
  }

  protected saveSupplierInvoice(): void {
    this.saving.set(true);
    const form = this.supplierInvoiceForm;
    this.api
      .createSupplierInvoice({
        ...form,
        costCenterId: form.costCenterId || null,
      })
      .subscribe({
        next: () => this.afterMutation('Facture fournisseur enregistrée en brouillon.'),
        error: () => this.mutationFailed('Vérifiez les informations de la facture fournisseur.'),
      });
  }

  protected saveExpense(): void {
    this.saving.set(true);
    const form = this.expenseForm;
    this.api
      .createExpense({
        ...form,
        supplierId: form.supplierId || null,
        costCenterId: form.costCenterId || null,
      })
      .subscribe({
        next: () => this.afterMutation('Dépense enregistrée en brouillon.'),
        error: () => this.mutationFailed('Vérifiez les informations de la dépense.'),
      });
  }

  protected saveAccount(): void {
    this.saving.set(true);
    this.api.createAccount(this.accountForm).subscribe({
      next: () => this.afterMutation('Compte ajouté au plan comptable.'),
      error: () => this.mutationFailed('Ce compte ne peut pas être ajouté.'),
    });
  }

  protected saveReconciliation(): void {
    this.saving.set(true);
    this.api.createReconciliation(this.reconciliationForm).subscribe({
      next: () => this.afterMutation('Rapprochement créé ; les mouvements peuvent être pointés.'),
      error: () => this.mutationFailed('Vérifiez la période et les soldes du relevé.'),
    });
  }

  protected accountTypeLabel(type: string): string {
    return (
      {
        ASSET: 'Actif',
        LIABILITY: 'Passif',
        EQUITY: 'Capitaux',
        REVENUE: 'Produit',
        EXPENSE: 'Charge',
      }[type] ?? type
    );
  }

  protected sourceLabel(source: string): string {
    return (
      {
        FAMILY_LEDGER: 'Familles',
        SUPPLIER: 'Fournisseur',
        EXPENSE: 'Dépense',
        ACCOUNTING: 'Extourne',
      }[source] ?? source
    );
  }

  protected accountCount(type: string): number {
    return this.data()?.accounts.filter((account) => account.accountType === type).length ?? 0;
  }

  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Le cockpit comptable ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  private afterMutation(message: string): void {
    this.notice.set(message);
    this.saving.set(false);
    this.drawer.set(null);
    this.load(false);
  }

  private mutationFailed(message: string): void {
    this.error.set(message);
    this.saving.set(false);
  }

  private emptySupplierInvoice(dashboard: AccountingDashboard | null = null): SupplierInvoiceForm {
    return {
      supplierId: dashboard?.suppliers[0]?.id ?? '',
      journalId: dashboard?.journals.find((journal) => journal.code === 'ACH')?.id ?? '',
      expenseAccountId:
        dashboard?.accounts.find((account) => account.accountType === 'EXPENSE')?.id ?? '',
      payableAccountId:
        dashboard?.accounts.find((account) => account.accountType === 'LIABILITY')?.id ?? '',
      costCenterId: dashboard?.costCenters[0]?.id ?? '',
      invoiceNumber: '',
      invoiceDate: '2026-09-01',
      dueDate: '2026-10-01',
      description: '',
      totalAmount: 0,
      currency: 'MAD',
    };
  }

  private emptyExpense(dashboard: AccountingDashboard | null = null): ExpenseForm {
    return {
      supplierId: dashboard?.suppliers[0]?.id ?? '',
      journalId:
        dashboard?.journals.find((journal) => ['CAI', 'BNK'].includes(journal.code))?.id ?? '',
      expenseAccountId:
        dashboard?.accounts.find((account) => account.accountType === 'EXPENSE')?.id ?? '',
      paymentAccountId:
        dashboard?.accounts.find((account) => account.accountType === 'ASSET')?.id ?? '',
      costCenterId: dashboard?.costCenters[0]?.id ?? '',
      expenseReference: '',
      expenseDate: '2026-09-01',
      description: '',
      amount: 0,
      currency: 'MAD',
      paymentMethod: 'BANK_TRANSFER',
    };
  }
}
