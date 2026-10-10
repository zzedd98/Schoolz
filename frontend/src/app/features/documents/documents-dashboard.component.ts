import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';

import {
  DocumentDashboard,
  DocumentsApiService,
  DocumentType,
  PdfTemplate,
  SchoolDocument,
} from '../../core/documents/documents-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-documents-dashboard',
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './documents-dashboard.component.html',
  styleUrl: './documents-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentsDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(DocumentsApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dashboard = signal<DocumentDashboard | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected pdfTemplate: PdfTemplate = 'SCHOOL_CERTIFICATE';
  protected pdfTitle = 'Attestation de scolarité';
  protected pdfRecipient = 'Yasmine El Mansouri';
  protected pdfReference = 'SCOLARITE-2026';
  protected pdfParentId = '';
  protected pdfExpiresOn = '';

  protected uploadCategoryId = '';
  protected uploadType: DocumentType = 'PARENT_ID';
  protected uploadTitle = "Pièce d'identité parent";
  protected uploadExpiresOn = '';
  protected uploadFile: File | null = null;
  protected versionFile: File | null = null;
  protected shareParentId = '';

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selected(): SchoolDocument | null {
    return this.dashboard()?.documents.find((item) => item.id === this.selectedId()) ?? null;
  }

  protected select(document: SchoolDocument): void {
    this.selectedId.set(document.id);
    this.versionFile = null;
  }

  protected fileSelected(event: Event, target: 'upload' | 'version'): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    if (target === 'upload') this.uploadFile = file;
    else this.versionFile = file;
  }

  protected generate(): void {
    const dashboard = this.dashboard();
    if (!dashboard || !this.pdfTitle.trim() || !this.pdfRecipient.trim()) return;
    const categoryCode = this.typeForTemplate(this.pdfTemplate);
    const category = dashboard.categories.find((item) => item.code === categoryCode);
    if (!category) return;
    this.mutate(
      this.api.generate({
        categoryId: category.id,
        template: this.pdfTemplate,
        title: this.pdfTitle.trim(),
        description: 'PDF généré depuis le cockpit documentaire SchoolZ.',
        recipient: this.pdfRecipient.trim(),
        reference: this.pdfReference.trim(),
        ownerKind: this.pdfParentId ? 'PARENT' : 'SCHOOL',
        ownerReferenceId: this.pdfParentId || null,
        visibility: this.pdfParentId ? 'FAMILY' : 'INTERNAL',
        expiresOn: this.pdfExpiresOn || null,
        fields: {
          Établissement: 'École Démo SchoolZ',
          Objet: this.pdfTitle.trim(),
          Traçabilité: 'Version PDF et empreinte SHA-256 conservées dans le DMS.',
        },
      }),
      this.text('PDF généré et versionné.', 'تم إنشاء ملف PDF وحفظ نسخته.'),
    );
  }

  protected upload(): void {
    if (!this.uploadFile || !this.uploadCategoryId || !this.uploadTitle.trim()) return;
    this.mutate(
      this.api.upload(
        {
          categoryId: this.uploadCategoryId,
          documentType: this.uploadType,
          title: this.uploadTitle.trim(),
          description: 'Document chargé dans le DMS SchoolZ.',
          ownerKind: 'SCHOOL',
          ownerReferenceId: null,
          visibility: 'INTERNAL',
          expiresOn: this.uploadExpiresOn || null,
        },
        this.uploadFile,
      ),
      this.text('Document chargé avec sa version 1.', 'تم تحميل الوثيقة مع النسخة الأولى.'),
      () => (this.uploadFile = null),
    );
  }

  protected addVersion(): void {
    const document = this.selected();
    if (!document || !this.versionFile) return;
    this.mutate(
      this.api.addVersion(document.id, this.versionFile),
      this.text('Nouvelle version ajoutée.', 'تمت إضافة نسخة جديدة.'),
      () => (this.versionFile = null),
    );
  }

  protected share(): void {
    const document = this.selected();
    if (!document || !this.shareParentId) return;
    this.mutate(
      this.api.share(document.id, this.shareParentId),
      this.text('Accès famille accordé.', 'تم منح الأسرة صلاحية الوصول.'),
    );
  }

  protected download(document: SchoolDocument): void {
    this.busy.set(true);
    this.api
      .signedUrl(document.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (signed) => {
          window.open(signed.url, '_blank', 'noopener,noreferrer');
          this.notice.set(
            this.text(
              `Lien signé valable jusqu'à ${new Date(signed.expiresAt).toLocaleTimeString()}.`,
              'تم إنشاء رابط مؤقت وموقّع.',
            ),
          );
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  protected templateLabel(template: PdfTemplate): string {
    const labels: Record<PdfTemplate, string> = {
      SCHOOL_CERTIFICATE: 'Attestation de scolarité',
      RECEIPT: 'Reçu',
      INVOICE: 'Facture',
      CERTIFICATE: 'Certificat',
      REPORT_CARD: 'Bulletin',
      CONTRACT: 'Contrat',
    };
    return labels[template];
  }

  private load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .dashboard()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.pdfParentId ||= dashboard.parents[0]?.id ?? '';
          this.shareParentId ||= dashboard.parents[0]?.id ?? '';
          this.uploadCategoryId ||= dashboard.categories[0]?.id ?? '';
          if (!dashboard.documents.some((item) => item.id === this.selectedId())) {
            this.selectedId.set(dashboard.documents[0]?.id ?? null);
          }
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  private mutate(request: Observable<unknown>, success: string, reset?: () => void): void {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          reset?.();
          this.notice.set(success);
          this.load();
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  private failure(response: { error?: { detail?: string } }): void {
    this.error.set(
      response.error?.detail ??
        this.text("L'opération documentaire a échoué.", 'تعذر تنفيذ العملية على الوثيقة.'),
    );
  }

  private typeForTemplate(template: PdfTemplate): string {
    const types: Record<PdfTemplate, string> = {
      SCHOOL_CERTIFICATE: 'ATTESTATION',
      RECEIPT: 'INVOICE',
      INVOICE: 'INVOICE',
      CERTIFICATE: 'CERTIFICATE',
      REPORT_CARD: 'REPORT_CARD',
      CONTRACT: 'CONTRACT',
    };
    return types[template];
  }
}
