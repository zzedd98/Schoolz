import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';

import {
  DocumentSignaturesApiService,
  SignatureDashboard,
  SignatureStatus,
  SignatureWorkflow,
} from '../../core/documents/document-signatures-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-document-signatures-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './document-signatures-dashboard.component.html',
  styleUrl: './document-signatures-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentSignaturesDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(DocumentSignaturesApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dashboard = signal<SignatureDashboard | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected documentId = '';
  protected parentId = '';
  protected readonly statuses: readonly SignatureStatus[] = [
    'GENERATED',
    'SENT',
    'VIEWED',
    'ACCEPTED',
    'SIGNED',
  ];

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selected(): SignatureWorkflow | null {
    return this.dashboard()?.workflows.find((item) => item.id === this.selectedId()) ?? null;
  }

  protected select(workflow: SignatureWorkflow): void {
    this.selectedId.set(workflow.id);
  }

  protected create(): void {
    if (!this.documentId || !this.parentId) return;
    this.mutate(
      this.api.create(this.documentId, this.parentId),
      this.text('Workflow généré sur la version courante.', 'تم إنشاء المسار على النسخة الحالية.'),
    );
  }

  protected send(): void {
    const workflow = this.selected();
    if (!workflow || workflow.status !== 'GENERATED') return;
    this.mutate(
      this.api.send(workflow.id),
      this.text(
        'Document envoyé et accès famille accordé.',
        'تم إرسال الوثيقة ومنح الأسرة حق الوصول.',
      ),
    );
  }

  protected reached(workflow: SignatureWorkflow, status: SignatureStatus): boolean {
    return this.statuses.indexOf(workflow.status) >= this.statuses.indexOf(status);
  }

  protected timestamp(workflow: SignatureWorkflow, status: SignatureStatus): string | null {
    const values: Record<SignatureStatus, string | null> = {
      GENERATED: workflow.generatedAt,
      SENT: workflow.sentAt,
      VIEWED: workflow.viewedAt,
      ACCEPTED: workflow.acceptedAt,
      SIGNED: workflow.signedAt,
    };
    return values[status];
  }

  private load(): void {
    this.busy.set(true);
    this.api
      .dashboard()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.documentId ||= dashboard.documents[0]?.id ?? '';
          this.parentId ||= dashboard.parents[0]?.id ?? '';
          if (!dashboard.workflows.some((item) => item.id === this.selectedId())) {
            this.selectedId.set(dashboard.workflows[0]?.id ?? null);
          }
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  private mutate(request: Observable<unknown>, success: string): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notice.set(success);
          this.load();
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  private failure(response: { error?: { detail?: string } }): void {
    this.error.set(
      response.error?.detail ??
        this.text('Le workflow de signature a échoué.', 'تعذر تنفيذ مسار التوقيع.'),
    );
  }
}
