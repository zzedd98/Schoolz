import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  ApiClientView,
  ApiKeyCommand,
  PublicApiAccessService,
  PublicApiDashboard,
} from '../../core/public-api/public-api-access.service';

@Component({
  selector: 'app-public-api-center',
  imports: [DatePipe, FormsModule],
  templateUrl: './public-api-center.component.html',
  styleUrl: './public-api-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicApiCenterComponent {
  private readonly api = inject(PublicApiAccessService);

  protected readonly data = signal<PublicApiDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly drawerOpen = signal(false);
  protected readonly revealedKey = signal('');
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly revokeReason = signal<Record<string, string>>({});
  protected form: ApiKeyCommand = this.emptyForm();

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
        this.error.set(response?.error?.detail ?? 'Le centre API ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  protected openCreator(): void {
    this.form = this.emptyForm();
    this.revealedKey.set('');
    this.drawerOpen.set(true);
  }

  protected hasScope(scope: string): boolean {
    return this.form.scopes.includes(scope);
  }

  protected toggleScope(scope: string, checked: boolean): void {
    this.form.scopes = checked
      ? [...this.form.scopes.filter((item) => item !== scope), scope]
      : this.form.scopes.filter((item) => item !== scope);
  }

  protected create(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.create(this.form).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.revealedKey.set(created.apiKey);
        this.success.set('La clé a été créée. Copiez-la maintenant : elle ne sera plus affichée.');
        this.load();
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(response?.error?.detail ?? 'La clé API n’a pas pu être créée.');
      },
    });
  }

  protected reason(clientId: string): string {
    return this.revokeReason()[clientId] ?? '';
  }

  protected setReason(clientId: string, value: string): void {
    this.revokeReason.update((current) => ({ ...current, [clientId]: value }));
  }

  protected revoke(client: ApiClientView): void {
    const reason = this.reason(client.id).trim();
    if (!reason) {
      this.error.set('Un motif est obligatoire pour révoquer une clé.');
      return;
    }
    this.busy.set(true);
    this.api.revoke(client.id, reason).subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set(`${client.name} ne peut plus appeler l’API.`);
        this.load();
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(response?.error?.detail ?? 'La clé n’a pas pu être révoquée.');
      },
    });
  }

  protected outcomeName(value: string): string {
    return (
      {
        SUCCESS: 'Réussie',
        UNAUTHORIZED_SCOPE: 'Scope refusé',
        RATE_LIMITED: 'Quota atteint',
        FAILED: 'Échec',
      }[value] ?? value
    );
  }

  private emptyForm(): ApiKeyCommand {
    return {
      name: '',
      description: '',
      scopes: ['students:read'],
      rateLimitPerMinute: 60,
      expiresAt: null,
    };
  }
}
