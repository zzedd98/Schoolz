import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  NotificationCenterApiService,
  NotificationCenterResponse,
  NotificationChannel,
  NotificationCenterItem,
  NotificationPreference,
} from '../../core/notifications/notification-center-api.service';

@Component({
  selector: 'app-notification-center',
  imports: [DatePipe, RouterLink],
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationCenterComponent {
  private readonly api = inject(NotificationCenterApiService);

  protected readonly data = signal<NotificationCenterResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected status = 'ALL';

  protected readonly filters = [
    { value: 'ALL', label: 'À consulter' },
    { value: 'UNREAD', label: 'Non lues' },
    { value: 'READ', label: 'Lues' },
    { value: 'ARCHIVED', label: 'Archivées' },
  ] as const;

  constructor() {
    this.load();
  }

  protected load(page = 0): void {
    this.loading.set(true);
    this.error.set('');
    this.api.center(this.status, page).subscribe({
      next: (result) => {
        this.data.set(result);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(
          response?.error?.detail ?? 'Le centre de notifications ne peut pas être chargé.',
        );
      },
    });
  }

  protected selectStatus(status: string): void {
    this.status = status;
    this.load();
  }

  protected read(item: NotificationCenterItem): void {
    if (item.state !== 'UNREAD') return;
    this.mutate(() => this.api.read(item.id), 'Notification marquée comme lue.');
  }

  protected archive(item: NotificationCenterItem): void {
    this.mutate(() => this.api.archive(item.id), 'Notification archivée.');
  }

  protected toggle(preference: NotificationPreference): void {
    if (preference.mandatory && preference.enabled) {
      this.error.set('Ce canal protège les alertes critiques de l’établissement.');
      return;
    }
    const channels = Object.fromEntries(
      (this.data()?.preferences ?? []).map((item) => [
        item.channel,
        item.channel === preference.channel ? !item.enabled : item.enabled,
      ]),
    ) as Record<NotificationChannel, boolean>;
    this.busy.set(true);
    this.api.preferences(channels).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('Vos préférences ont été enregistrées.');
        this.load(this.data()?.page ?? 0);
      },
      error: (response) => this.fail(response, 'Les préférences ne peuvent pas être enregistrées.'),
    });
  }

  protected channelName(channel: NotificationChannel): string {
    return { PUSH: 'Push', EMAIL: 'E-mail', SMS: 'SMS' }[channel];
  }

  private mutate(
    action: () => ReturnType<NotificationCenterApiService['read']>,
    message: string,
  ): void {
    this.busy.set(true);
    this.error.set('');
    action().subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set(message);
        this.load(this.data()?.page ?? 0);
      },
      error: (response) => this.fail(response, 'La notification ne peut pas être modifiée.'),
    });
  }

  private fail(response: { error?: { detail?: string } } | null, fallback: string): void {
    this.busy.set(false);
    this.error.set(response?.error?.detail ?? fallback);
  }
}
