import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  WebhookApiService,
  WebhookDashboard,
  WebhookDelivery,
  WebhookSubscription,
  WebhookSubscriptionCommand,
} from '../../core/webhooks/webhook-api.service';

@Component({
  selector: 'app-webhook-center',
  imports: [DatePipe, FormsModule],
  templateUrl: './webhook-center.component.html',
  styleUrl: './webhook-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WebhookCenterComponent {
  private readonly api = inject(WebhookApiService);

  protected readonly data = signal<WebhookDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly drawerOpen = signal(false);
  protected readonly revealedSecret = signal('');
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly reasons = signal<Record<string, string>>({});
  protected form: WebhookSubscriptionCommand = this.emptyForm();

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(response?.error?.detail ?? 'Le centre de webhooks ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  protected openCreator(): void {
    this.form = this.emptyForm();
    this.revealedSecret.set('');
    this.drawerOpen.set(true);
  }

  protected hasEvent(eventType: string): boolean {
    return this.form.eventTypes.includes(eventType);
  }

  protected toggleEvent(eventType: string, checked: boolean): void {
    this.form.eventTypes = checked
      ? [...this.form.eventTypes.filter((value) => value !== eventType), eventType]
      : this.form.eventTypes.filter((value) => value !== eventType);
  }

  protected create(): void {
    this.busy.set(true);
    this.clearMessages();
    this.api.create(this.form).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.revealedSecret.set(created.signingSecret);
        this.success.set('Abonnement créé. Copiez maintenant son secret de signature.');
        this.load();
      },
      error: (response) => this.fail(response, 'L’abonnement n’a pas pu être créé.'),
    });
  }

  protected publishDemo(): void {
    this.busy.set(true);
    this.clearMessages();
    this.api
      .publish('student.enrolled', `demo-${Date.now()}`, {
        studentNumber: 'SZ-2026-DEMO',
        classroom: '1AP — Groupe A',
        source: 'console-m38',
      })
      .subscribe({
        next: (event) => {
          this.busy.set(false);
          this.success.set(`${event.deliveryCount} livraison(s) idempotente(s) mise(s) en file.`);
          this.load();
        },
        error: (response) => this.fail(response, 'L’événement n’a pas pu être publié.'),
      });
  }

  protected processDue(): void {
    this.busy.set(true);
    this.clearMessages();
    this.api.processDue().subscribe({
      next: (result) => {
        this.busy.set(false);
        this.success.set(
          `${result.processed} traitée(s) : ${result.delivered} livrée(s), ${result.deferred} reportée(s), ${result.deadLettered} en dead letter.`,
        );
        this.load();
      },
      error: (response) => this.fail(response, 'La file n’a pas pu être traitée.'),
    });
  }

  protected reason(id: string): string {
    return this.reasons()[id] ?? '';
  }

  protected setReason(id: string, value: string): void {
    this.reasons.update((current) => ({ ...current, [id]: value }));
  }

  protected revoke(subscription: WebhookSubscription): void {
    const reason = this.reason(subscription.id).trim();
    if (!reason) {
      this.error.set('Un motif est requis pour désactiver un abonnement.');
      return;
    }
    this.busy.set(true);
    this.api.revoke(subscription.id, reason).subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set(`${subscription.name} a été désactivé.`);
        this.load();
      },
      error: (response) => this.fail(response, 'L’abonnement n’a pas pu être désactivé.'),
    });
  }

  protected retry(delivery: WebhookDelivery): void {
    const reason = this.reason(delivery.id).trim();
    if (!reason) {
      this.error.set('Un motif est requis pour rejouer une livraison.');
      return;
    }
    this.busy.set(true);
    this.api.retry(delivery.id, reason).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.success.set(`Reprise terminée avec le statut ${this.statusName(result.status)}.`);
        this.load();
      },
      error: (response) => this.fail(response, 'La livraison n’a pas pu être rejouée.'),
    });
  }

  protected statusName(status: WebhookDelivery['status']): string {
    return (
      {
        PENDING: 'En attente',
        RETRY_SCHEDULED: 'Nouvel essai planifié',
        DELIVERED: 'Livrée',
        DEAD_LETTER: 'Dead letter',
      }[status] ?? status
    );
  }

  private fail(response: { error?: { detail?: string } } | null, fallback: string): void {
    this.busy.set(false);
    this.error.set(response?.error?.detail ?? fallback);
  }

  private clearMessages(): void {
    this.error.set('');
    this.success.set('');
  }

  private emptyForm(): WebhookSubscriptionCommand {
    return {
      name: '',
      endpointUrl: 'https://',
      eventTypes: ['student.created'],
      maxAttempts: 5,
      timeoutSeconds: 10,
    };
  }
}
