import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  CommunicationChannel,
  CommunicationCategory,
  CommunicationsApiService,
  Conversation,
  FamilyCommunicationDashboard,
  NotificationPreference,
} from '../../core/communications/communications-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-communications-portal',
  imports: [DatePipe, FormsModule],
  templateUrl: './communications-portal.component.html',
  styleUrl: './communications-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommunicationsPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(CommunicationsApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  protected readonly dashboard = signal<FamilyCommunicationDashboard | null>(null);
  protected readonly preferenceDraft = signal<readonly NotificationPreference[]>([]);
  protected readonly selectedConversationId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly authRequired = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected replyBody = '';

  protected readonly categories: readonly CommunicationCategory[] = [
    'ANNOUNCEMENT',
    'DIRECT_MESSAGE',
    'AUTOMATED_NOTIFICATION',
  ];
  protected readonly channels: readonly CommunicationChannel[] = [
    'IN_APP',
    'PUSH',
    'EMAIL',
    'SMS',
    'WHATSAPP',
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

  protected logout(): void {
    this.admissions
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.redirectToLogin(false),
        error: () => this.redirectToLogin(false),
      });
  }

  protected selectedConversation(): Conversation | null {
    const selected = this.selectedConversationId();
    return this.dashboard()?.conversations.find((item) => item.id === selected) ?? null;
  }

  protected selectConversation(id: string): void {
    this.selectedConversationId.set(id);
    this.replyBody = '';
  }

  protected reply(): void {
    const conversation = this.selectedConversation();
    if (!conversation || !this.replyBody.trim()) return;
    this.busy.set(true);
    this.api
      .familyReply(conversation.id, this.replyBody.trim())
      .pipe(
        switchMap(() => this.api.familyDashboard()),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.replyBody = '';
          this.notice.set(this.text('Votre réponse a été envoyée.', 'تم إرسال ردكم.'));
          this.acceptDashboard(dashboard);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text("La réponse n'a pas été envoyée.", 'لم يتم إرسال الرد.'),
          ),
      });
  }

  protected read(notificationId: string): void {
    this.api
      .readFamily(notificationId)
      .pipe(
        switchMap(() => this.api.familyDashboard()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: (dashboard) => this.acceptDashboard(dashboard) });
  }

  protected preference(category: CommunicationCategory, channel: CommunicationChannel): boolean {
    return (
      this.preferenceDraft().find((item) => item.category === category && item.channel === channel)
        ?.enabled ?? false
    );
  }

  protected togglePreference(category: CommunicationCategory, channel: CommunicationChannel): void {
    this.preferenceDraft.update((items) =>
      items.map((item) =>
        item.category === category && item.channel === channel
          ? { ...item, enabled: !item.enabled }
          : item,
      ),
    );
  }

  protected savePreferences(): void {
    this.busy.set(true);
    this.api
      .updatePreferences(this.preferenceDraft())
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preferences) => {
          this.preferenceDraft.set(preferences);
          this.notice.set(this.text('Vos préférences sont enregistrées.', 'تم حفظ تفضيلاتكم.'));
        },
        error: () =>
          this.error.set(
            this.text('Les préférences ne peuvent pas être enregistrées.', 'تعذر حفظ التفضيلات.'),
          ),
      });
  }

  protected categoryLabel(category: CommunicationCategory): string {
    const labels: Record<CommunicationCategory, [string, string]> = {
      ANNOUNCEMENT: ['Annonces', 'الإعلانات'],
      DIRECT_MESSAGE: ['Messages directs', 'الرسائل المباشرة'],
      AUTOMATED_NOTIFICATION: ['Notifications automatiques', 'الإشعارات الآلية'],
    };
    return this.i18n.locale() === 'ar-MA' ? labels[category][1] : labels[category][0];
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
        error: (response: { status?: number }) => {
          this.authRequired.set(response.status === 401);
          if (response.status === 401) {
            this.redirectToLogin(true);
            return;
          }
          if (response.status !== 401) {
            this.error.set(
              this.text('Le centre de messages est indisponible.', 'مركز الرسائل غير متاح.'),
            );
          }
        },
      });
  }

  private acceptDashboard(dashboard: FamilyCommunicationDashboard): void {
    this.dashboard.set(dashboard);
    this.preferenceDraft.set(dashboard.preferences.map((item) => ({ ...item })));
    this.authRequired.set(false);
    const selectedExists = dashboard.conversations.some(
      (item) => item.id === this.selectedConversationId(),
    );
    if (!selectedExists) this.selectedConversationId.set(dashboard.conversations[0]?.id ?? null);
  }

  private redirectToLogin(returnToPortal: boolean): void {
    this.dashboard.set(null);
    void this.router.navigate(['/login'], {
      ...(returnToPortal ? { queryParams: { portal: 'family', returnUrl: this.router.url } } : {}),
      replaceUrl: true,
    });
  }
}
