import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import {
  CommunicationDashboard,
  CommunicationsApiService,
  Conversation,
  ParentOption,
} from '../../core/communications/communications-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-communications-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './communications-dashboard.component.html',
  styleUrl: './communications-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommunicationsDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(CommunicationsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  protected readonly dashboard = signal<CommunicationDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly selectedConversationId = signal<string | null>(null);

  protected announcementScope: 'SCHOOL' | 'CAMPUS' | 'CLASS' = 'SCHOOL';
  protected announcementCampusId = '';
  protected announcementClassroomId = '';
  protected announcementTitle = '';
  protected announcementBody = '';
  protected conversationParentKey = '';
  protected conversationSubject = '';
  protected conversationBody = '';
  protected replyBody = '';
  protected automatedParentKey = '';
  protected automatedTemplateKey = 'attendance-follow-up';
  protected automatedStudentName = 'Yasmine';

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectedConversation(): Conversation | null {
    const selected = this.selectedConversationId();
    return this.dashboard()?.conversations.find((item) => item.id === selected) ?? null;
  }

  protected uniqueTemplateKeys(): readonly string[] {
    return [...new Set(this.dashboard()?.templates.map((item) => item.key) ?? [])];
  }

  protected chooseConversation(id: string): void {
    this.selectedConversationId.set(id);
    this.replyBody = '';
  }

  protected publishAnnouncement(): void {
    if (!this.announcementTitle.trim() || !this.announcementBody.trim()) return;
    this.mutate(
      this.api.publishAnnouncement({
        title: this.announcementTitle.trim(),
        body: this.announcementBody.trim(),
        audienceScope: this.announcementScope,
        campusId: this.announcementScope === 'CAMPUS' ? this.announcementCampusId : null,
        classroomId: this.announcementScope === 'CLASS' ? this.announcementClassroomId : null,
      }),
      this.text('Annonce publiée et distribuée.', 'تم نشر الإعلان وتوزيعه.'),
      () => {
        this.announcementTitle = '';
        this.announcementBody = '';
      },
    );
  }

  protected startConversation(): void {
    const parent = this.parentFromKey(this.conversationParentKey);
    if (!parent || !this.conversationSubject.trim() || !this.conversationBody.trim()) return;
    this.mutate(
      this.api.startConversation({
        portalAccountId: parent.portalAccountId,
        studentId: parent.studentId,
        subject: this.conversationSubject.trim(),
        body: this.conversationBody.trim(),
      }),
      this.text('Conversation ouverte.', 'تم فتح المحادثة.'),
      () => {
        this.conversationSubject = '';
        this.conversationBody = '';
      },
    );
  }

  protected reply(): void {
    const conversation = this.selectedConversation();
    if (!conversation || !this.replyBody.trim()) return;
    this.mutate(
      this.api.reply(conversation.id, this.replyBody.trim()),
      this.text('Réponse envoyée dans le fil.', 'تم إرسال الرد في المحادثة.'),
      () => (this.replyBody = ''),
    );
  }

  protected sendAutomated(): void {
    const parent = this.parentFromKey(this.automatedParentKey);
    if (!parent || !this.automatedTemplateKey) return;
    this.mutate(
      this.api.sendAutomated({
        templateKey: this.automatedTemplateKey,
        portalAccountId: parent.portalAccountId,
        studentId: parent.studentId,
        variables: { student: this.automatedStudentName.trim() || parent.studentName },
      }),
      this.text('Notification automatisée préparée.', 'تم إعداد الإشعار الآلي.'),
    );
  }

  protected readNotification(id: string): void {
    this.api
      .readInternal(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.load() });
  }

  protected audienceLabel(scope: string): string {
    const labels: Record<string, [string, string]> = {
      SCHOOL: ['École entière', 'المؤسسة كاملة'],
      CAMPUS: ['Campus', 'الفرع'],
      CLASS: ['Classe', 'القسم'],
    };
    const label = labels[scope] ?? [scope, scope];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
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
          if (dashboard.teacherView) this.announcementScope = 'CLASS';
          const requestedClassroom = this.route.snapshot.queryParamMap.get('classroomId');
          this.announcementClassroomId ||=
            dashboard.classrooms.find((item) => item.id === requestedClassroom)?.id ??
            dashboard.classrooms[0]?.id ??
            '';
          this.announcementCampusId ||= dashboard.campuses[0]?.id ?? '';
          const firstParent = dashboard.parents[0];
          const firstKey = firstParent ? this.parentKey(firstParent) : '';
          this.conversationParentKey ||= firstKey;
          this.automatedParentKey ||= firstKey;
          const selectedExists = dashboard.conversations.some(
            (item) => item.id === this.selectedConversationId(),
          );
          if (!selectedExists)
            this.selectedConversationId.set(dashboard.conversations[0]?.id ?? null);
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text(
                'Le centre de communications est indisponible.',
                'مركز التواصل غير متاح حالياً.',
              ),
          ),
      });
  }

  private mutate(
    request: import('rxjs').Observable<unknown>,
    success: string,
    reset?: () => void,
  ): void {
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
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text("L'action n'a pas pu être effectuée.", 'تعذر تنفيذ العملية.'),
          ),
      });
  }

  protected parentKey(parent: ParentOption): string {
    return `${parent.portalAccountId}:${parent.studentId}`;
  }

  private parentFromKey(key: string): ParentOption | null {
    return this.dashboard()?.parents.find((item) => this.parentKey(item) === key) ?? null;
  }
}
