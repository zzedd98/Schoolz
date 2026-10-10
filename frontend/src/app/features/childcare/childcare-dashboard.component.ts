import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Observable, finalize } from 'rxjs';

import {
  ChildcareApiService,
  ChildcareChild,
  ChildcareDashboard,
  ChildcareEvent,
  ChildcareEventType,
  EventCommand,
} from '../../core/childcare/childcare-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

type EditorAction =
  Exclude<ChildcareEventType, 'MEAL' | 'BOTTLE' | 'DIAPER' | 'TOILET'> | 'DEPARTURE' | 'PUBLISH';

@Component({
  selector: 'app-childcare-dashboard',
  imports: [FormsModule, MatButtonModule],
  templateUrl: './childcare-dashboard.component.html',
  styleUrl: './childcare-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChildcareDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(ChildcareApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly date = signal(this.localDate(new Date()));
  protected readonly selectedGroup = signal('');
  protected readonly dashboard = signal<ChildcareDashboard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly activeStudent = signal<ChildcareChild | null>(null);
  protected readonly activeAction = signal<EditorAction | null>(null);

  protected formLabel = '';
  protected formNotes = '';
  protected formStart = this.localTime(new Date());
  protected formEnd = this.localTime(new Date(Date.now() + 60 * 60 * 1000));
  protected formMood = 'HAPPY';
  protected formTemperature = '';
  protected formSeverity = 'LOW';
  protected formMedication = '';
  protected formDose = '';
  protected formAuthorization = '';
  protected formPickup = '';

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectDate(event: Event): void {
    this.date.set((event.target as HTMLInputElement).value);
    this.load();
  }

  protected selectGroup(event: Event): void {
    this.selectedGroup.set((event.target as HTMLSelectElement).value);
    this.load();
  }

  protected load(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .dashboard(this.date(), this.selectedGroup() || undefined)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => this.dashboard.set(dashboard),
        error: () =>
          this.error.set(
            this.text(
              'Le tableau petite enfance est indisponible.',
              'لوحة الطفولة المبكرة غير متاحة.',
            ),
          ),
      });
  }

  protected quickArrival(child: ChildcareChild): void {
    this.mutate(
      this.api.arrival(child.studentId),
      this.text('Arrivée enregistrée.', 'تم تسجيل الوصول.'),
    );
  }

  protected quickEvent(
    child: ChildcareChild,
    eventType: 'MEAL' | 'BOTTLE' | 'DIAPER' | 'TOILET',
  ): void {
    const command: EventCommand =
      eventType === 'MEAL'
        ? { eventType, outcome: 'COMPLETED' }
        : eventType === 'BOTTLE'
          ? { eventType, quantity: 150, unit: 'ml' }
          : { eventType };
    this.mutate(
      this.api.addEvent(child.studentId, command),
      this.text('Événement ajouté au journal.', 'تمت إضافة الحدث إلى السجل.'),
    );
  }

  protected openEditor(child: ChildcareChild, action: EditorAction): void {
    this.activeStudent.set(child);
    this.activeAction.set(action);
    this.formLabel = '';
    this.formNotes = action === 'PUBLISH' ? (child.dailyLog.summary ?? 'Très bonne journée.') : '';
    this.formStart = this.localTime(new Date());
    this.formEnd = this.localTime(new Date(Date.now() + 60 * 60 * 1000));
    this.formMood = 'HAPPY';
    this.formTemperature = '';
    this.formSeverity = 'LOW';
    this.formMedication = '';
    this.formDose = '';
    this.formAuthorization = '';
    this.formPickup =
      child.pickupPeople.find((person) => person.authorizationStatus === 'AUTHORIZED')?.id ?? '';
  }

  protected closeEditor(): void {
    this.activeAction.set(null);
    this.activeStudent.set(null);
  }

  protected submitEditor(): void {
    const child = this.activeStudent();
    const action = this.activeAction();
    if (!child || !action) return;

    if (action === 'DEPARTURE') {
      if (!this.formPickup) return;
      this.mutate(
        this.api.departure(child.studentId, this.formPickup),
        this.text('Départ sécurisé enregistré.', 'تم تسجيل المغادرة الآمنة.'),
      );
      this.closeEditor();
      return;
    }
    if (action === 'PUBLISH') {
      this.mutate(
        this.api.publish(child.studentId, this.date(), this.formNotes),
        this.text('Daily Report publié au parent.', 'تم نشر التقرير اليومي للوالدين.'),
      );
      this.closeEditor();
      return;
    }

    let command: EventCommand = {
      eventType: action,
      occurredAt: this.instant(this.formStart),
      notes: this.formNotes || undefined,
    };
    if (action === 'SLEEP') command = { ...command, endedAt: this.instant(this.formEnd) };
    if (action === 'ACTIVITY' || action === 'OBSERVATION' || action === 'INCIDENT') {
      command = { ...command, label: this.formLabel || undefined };
    }
    if (action === 'MOOD') command = { ...command, outcome: this.formMood };
    if (action === 'TEMPERATURE') {
      command = { ...command, temperatureCelsius: Number(this.formTemperature) };
    }
    if (action === 'INCIDENT') command = { ...command, incidentSeverity: this.formSeverity };
    if (action === 'MEDICATION') {
      command = {
        ...command,
        medicationName: this.formMedication,
        medicationDose: this.formDose,
        authorizationReference: this.formAuthorization,
      };
    }
    this.mutate(
      this.api.addEvent(child.studentId, command),
      this.text('Événement détaillé enregistré.', 'تم تسجيل الحدث المفصل.'),
    );
    this.closeEditor();
  }

  protected events(child: ChildcareChild, type: ChildcareEventType): readonly ChildcareEvent[] {
    return child.dailyLog.events.filter((event) => event.eventType === type);
  }

  protected time(value: string | null): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }

  protected isLocked(child: ChildcareChild): boolean {
    return child.dailyLog.status === 'PUBLISHED';
  }

  protected presentCount(): number {
    return this.dashboard()?.children.filter((child) => child.dailyLog.arrivalAt).length ?? 0;
  }

  protected publishedCount(): number {
    return (
      this.dashboard()?.children.filter((child) => child.dailyLog.status === 'PUBLISHED').length ??
      0
    );
  }

  private mutate(request: Observable<ChildcareChild>, success: string): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (child) => {
          this.dashboard.update((dashboard) =>
            dashboard
              ? {
                  ...dashboard,
                  children: dashboard.children.map((current) =>
                    current.studentId === child.studentId ? child : current,
                  ),
                }
              : dashboard,
          );
          this.message.set(success);
        },
        error: (response: { error?: { message?: string } }) =>
          this.error.set(
            response.error?.message ??
              this.text("L'action n'a pas pu être enregistrée.", 'تعذر تسجيل العملية.'),
          ),
      });
  }

  private instant(time: string): string {
    return new Date(`${this.date()}T${time}:00`).toISOString();
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private localTime(date: Date): string {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }
}
