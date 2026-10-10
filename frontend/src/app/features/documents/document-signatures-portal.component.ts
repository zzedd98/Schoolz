import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, Observable, switchMap } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  DocumentSignaturesApiService,
  FamilySignatureDashboard,
  SignatureStatus,
  SignatureWorkflow,
} from '../../core/documents/document-signatures-api.service';
import { DocumentsApiService } from '../../core/documents/documents-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-document-signatures-portal',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './document-signatures-portal.component.html',
  styleUrl: './document-signatures-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentSignaturesPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(DocumentSignaturesApiService);
  private readonly documents = inject(DocumentsApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  protected readonly dashboard = signal<FamilySignatureDashboard | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly authRequired = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected consent = false;
  protected signatureConfirmed = false;
  protected signatoryName = 'Amina El Mansouri';
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

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected selected(): SignatureWorkflow | null {
    return this.dashboard()?.workflows.find((item) => item.id === this.selectedId()) ?? null;
  }

  protected choose(workflow: SignatureWorkflow): void {
    this.selectedId.set(workflow.id);
    this.consent = false;
    this.signatureConfirmed = false;
  }

  protected logout(): void {
    this.admissions
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.redirectToLogin(false),
        error: () => this.redirectToLogin(false),
      });
  }

  protected open(): void {
    const workflow = this.selected();
    if (!workflow || workflow.status !== 'SENT') return;
    this.mutate(
      this.api.view(workflow.id),
      this.text('Consultation horodatée.', 'تم تسجيل الاطلاع مع التوقيت.'),
    );
  }

  protected accept(): void {
    const workflow = this.selected();
    if (!workflow || workflow.status !== 'VIEWED' || !this.consent) return;
    this.mutate(
      this.api.accept(workflow.id),
      this.text('Acceptation horodatée.', 'تم تسجيل القبول مع التوقيت.'),
    );
  }

  protected sign(): void {
    const workflow = this.selected();
    if (
      !workflow ||
      workflow.status !== 'ACCEPTED' ||
      !this.signatureConfirmed ||
      !this.signatoryName.trim()
    )
      return;
    this.mutate(
      this.api.sign(workflow.id, this.signatoryName.trim()),
      this.text(
        'Signature électronique simple enregistrée.',
        'تم تسجيل التوقيع الإلكتروني البسيط.',
      ),
    );
  }

  protected download(): void {
    const workflow = this.selected();
    if (!workflow || workflow.status === 'GENERATED') return;
    this.documents
      .familySignedUrl(workflow.documentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (signed) => (window.location.href = signed.url) });
  }

  protected reached(workflow: SignatureWorkflow, status: SignatureStatus): boolean {
    return this.statuses.indexOf(workflow.status) >= this.statuses.indexOf(status);
  }

  private load(): void {
    this.busy.set(true);
    this.api
      .familyDashboard()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => this.acceptDashboard(dashboard),
        error: (response: { status?: number; error?: { detail?: string } }) => {
          if (response.status === 401) this.redirectToLogin(true);
          else this.failure(response);
        },
      });
  }

  private mutate(request: Observable<unknown>, success: string): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        switchMap(() => this.api.familyDashboard()),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.notice.set(success);
          this.acceptDashboard(dashboard);
        },
        error: (response: { error?: { detail?: string } }) => this.failure(response),
      });
  }

  private acceptDashboard(dashboard: FamilySignatureDashboard): void {
    this.dashboard.set(dashboard);
    this.authRequired.set(false);
    if (!dashboard.workflows.some((item) => item.id === this.selectedId())) {
      this.selectedId.set(dashboard.workflows[0]?.id ?? null);
    }
  }

  private redirectToLogin(returnToPortal: boolean): void {
    this.dashboard.set(null);
    this.authRequired.set(true);
    void this.router.navigate(['/login'], {
      ...(returnToPortal ? { queryParams: { portal: 'family', returnUrl: this.router.url } } : {}),
      replaceUrl: true,
    });
  }

  private failure(response: { error?: { detail?: string } }): void {
    this.error.set(
      response.error?.detail ?? this.text('Le parcours est indisponible.', 'المسار غير متاح.'),
    );
  }
}
