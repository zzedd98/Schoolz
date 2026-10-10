import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  DocumentsApiService,
  FamilyDocumentDashboard,
  SchoolDocument,
} from '../../core/documents/documents-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-documents-portal',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './documents-portal.component.html',
  styleUrl: './documents-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentsPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(DocumentsApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  protected readonly dashboard = signal<FamilyDocumentDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly authRequired = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
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

  protected download(document: SchoolDocument): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .familySignedUrl(document.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (signed) => {
          this.notice.set(
            this.text(
              `Lien limité à ${signed.maxDownloads} téléchargements, valable dix minutes.`,
              `الرابط صالح لعشر دقائق ومحدود في ${signed.maxDownloads} تنزيلات.`,
            ),
          );
          window.location.href = signed.url;
        },
        error: (response: { status?: number; error?: { detail?: string } }) => {
          if (response.status === 401) this.redirectToLogin(true);
          else this.failure(response);
        },
      });
  }

  protected typeLabel(type: string): string {
    const labels: Record<string, [string, string]> = {
      PARENT_ID: ["Pièce d'identité", 'بطاقة التعريف'],
      BIRTH_CERTIFICATE: ['Acte de naissance', 'رسم الولادة'],
      PHOTO: ['Photo', 'صورة'],
      CERTIFICATE: ['Certificat', 'شهادة'],
      CONTRACT: ['Contrat', 'عقد'],
      AUTHORIZATION: ['Autorisation', 'ترخيص'],
      REPORT_CARD: ['Bulletin', 'بيان النتائج'],
      INVOICE: ['Facture ou reçu', 'فاتورة أو إيصال'],
      ATTESTATION: ['Attestation', 'شهادة مدرسية'],
      INTERNAL: ['Document interne', 'وثيقة داخلية'],
    };
    const label = labels[type] ?? [type, type];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
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
        next: (dashboard) => this.accept(dashboard),
        error: (response: { status?: number; error?: { detail?: string } }) => {
          if (response.status === 401) this.redirectToLogin(true);
          else this.failure(response);
        },
      });
  }

  private accept(dashboard: FamilyDocumentDashboard): void {
    this.dashboard.set(dashboard);
    this.authRequired.set(false);
    this.error.set('');
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
      response.error?.detail ??
        this.text('Le coffre documentaire est indisponible.', 'خزنة الوثائق غير متاحة حالياً.'),
    );
  }
}
