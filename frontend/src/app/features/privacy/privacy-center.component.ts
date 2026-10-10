import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  HostingConfiguration,
  PrivacyApiService,
  PrivacyDashboard,
  PrivacyRequest,
  PrivacyRequestCommand,
  RetentionPolicy,
} from '../../core/privacy/privacy-api.service';

@Component({
  selector: 'app-privacy-center',
  imports: [DatePipe, FormsModule],
  templateUrl: './privacy-center.component.html',
  styleUrl: './privacy-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivacyCenterComponent {
  private readonly api = inject(PrivacyApiService);
  protected readonly data = signal<PrivacyDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected requestForm: PrivacyRequestCommand = {
    requestType: 'ACCESS',
    subjectType: 'STUDENT',
    subjectReference: '',
    notes: '',
  };
  protected hostingForm: HostingConfiguration = {
    countryCode: 'MA',
    regionLabel: '',
    providerName: '',
    encryptionAtRest: true,
    encryptionInTransit: true,
    crossBorderTransfer: false,
    legalValidationStatus: 'PENDING',
  };

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (result) => {
        this.data.set(result);
        if (result.hosting) this.hostingForm = { ...result.hosting };
        this.loading.set(false);
      },
      error: (response) => this.fail(response, 'Le registre ne peut pas être chargé.'),
    });
  }

  protected createRequest(): void {
    this.busy.set(true);
    this.api.createRequest(this.requestForm).subscribe({
      next: () => {
        this.requestForm = { ...this.requestForm, subjectReference: '', notes: '' };
        this.success('La demande a été enregistrée et horodatée.');
      },
      error: (response) => this.fail(response, 'La demande ne peut pas être créée.'),
    });
  }

  protected review(request: PrivacyRequest): void {
    this.busy.set(true);
    this.api.updateRequest(request.id, 'IN_REVIEW').subscribe({
      next: () => this.success('La demande est passée en revue.'),
      error: (response) => this.fail(response, 'Le statut ne peut pas être modifié.'),
    });
  }

  protected prepareExport(request: PrivacyRequest): void {
    this.busy.set(true);
    this.api.prepareExport(request).subscribe({
      next: () => this.success('La préparation de l’export est tracée pour 7 jours.'),
      error: (response) => this.fail(response, 'L’export ne peut pas être préparé.'),
    });
  }

  protected updateRetention(policy: RetentionPolicy, days: string): void {
    const retentionDays = Number(days);
    this.busy.set(true);
    this.api
      .configureRetention({
        dataCategory: policy.dataCategory,
        retentionDays,
        expiryAction: policy.expiryAction,
        legalHold: policy.legalHold,
      })
      .subscribe({
        next: () => this.success('La politique de conservation est historisée.'),
        error: (response) => this.fail(response, 'La politique ne peut pas être enregistrée.'),
      });
  }

  protected saveHosting(): void {
    this.busy.set(true);
    this.api.configureHosting(this.hostingForm).subscribe({
      next: () => this.success('La fiche d’hébergement est mise à jour.'),
      error: (response) => this.fail(response, 'La fiche ne peut pas être enregistrée.'),
    });
  }

  protected statusLabel(status: string): string {
    return (
      {
        RECEIVED: 'Reçue',
        IDENTITY_VERIFIED: 'Identité vérifiée',
        IN_REVIEW: 'En revue',
        FULFILLED: 'Traitée',
        REJECTED: 'Refusée',
        CANCELLED: 'Annulée',
      }[status] ?? status
    );
  }

  private success(message: string): void {
    this.busy.set(false);
    this.message.set(message);
    this.error.set('');
    this.load();
  }

  private fail(response: { error?: { detail?: string } } | null, fallback: string): void {
    this.loading.set(false);
    this.busy.set(false);
    this.error.set(response?.error?.detail ?? fallback);
  }
}
