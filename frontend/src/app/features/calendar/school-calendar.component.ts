import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import {
  SchoolCalendarApiService,
  SchoolCalendarCommand,
  SchoolCalendarEvent,
  SchoolCalendarView,
  SchoolEventType,
} from '../../core/calendar/school-calendar-api.service';

@Component({
  selector: 'app-school-calendar',
  imports: [DatePipe, FormsModule],
  templateUrl: './school-calendar.component.html',
  styleUrl: './school-calendar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SchoolCalendarComponent {
  private readonly api = inject(SchoolCalendarApiService);
  protected readonly auth = inject(AuthService);
  protected readonly data = signal<SchoolCalendarView | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly drawerOpen = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected type = '';
  protected campusId = '';
  protected rangeStart = this.startOfMonth(new Date());
  protected form = this.emptyForm();
  protected readonly labels: Readonly<Record<SchoolEventType, string>> = {
    HOLIDAY: 'Vacances',
    EXAM: 'Examens',
    MEETING: 'Réunions',
    TRIP: 'Sorties',
    ACTIVITY: 'Activités',
    PARENT_TEACHER: 'Parents–professeurs',
  };
  protected readonly icons: Readonly<Record<SchoolEventType, string>> = {
    HOLIDAY: '☀',
    EXAM: 'A+',
    MEETING: '◎',
    TRIP: '↗',
    ACTIVITY: '★',
    PARENT_TEACHER: '⌂',
  };
  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    const end = new Date(this.rangeStart);
    end.setMonth(end.getMonth() + 2);
    this.api
      .calendar(this.rangeStart.toISOString(), end.toISOString(), this.type, this.campusId)
      .subscribe({
        next: (result) => {
          this.data.set(result);
          this.loading.set(false);
        },
        error: (response) => {
          this.loading.set(false);
          this.error.set(response?.error?.detail ?? 'Le calendrier ne peut pas être chargé.');
        },
      });
  }
  protected filterType(type: string): void {
    this.type = type;
    this.load();
  }
  protected filterCampus(campus: string): void {
    this.campusId = campus;
    this.load();
  }
  protected shift(months: number): void {
    const next = new Date(this.rangeStart);
    next.setMonth(next.getMonth() + months);
    this.rangeStart = this.startOfMonth(next);
    this.load();
  }
  protected openCreator(): void {
    this.form = this.emptyForm();
    this.drawerOpen.set(true);
  }
  protected scopeChanged(): void {
    if (this.form.audienceScope === 'SCHOOL') delete this.form.campusId;
    else this.form.campusId = this.data()?.campuses[0]?.id ?? '';
  }
  protected create(): void {
    this.busy.set(true);
    const command = {
      ...this.form,
      startsAt: new Date(this.form.startsAt).toISOString(),
      endsAt: new Date(this.form.endsAt).toISOString(),
    };
    this.api.create(command).subscribe({
      next: () => {
        this.busy.set(false);
        this.drawerOpen.set(false);
        this.message.set('L’événement a rejoint le calendrier.');
        this.load();
      },
      error: (response) => this.fail(response, 'L’événement ne peut pas être créé.'),
    });
  }
  protected cancel(event: SchoolCalendarEvent): void {
    this.busy.set(true);
    this.api.cancel(event.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('L’événement a été annulé sans perdre son historique.');
        this.load();
      },
      error: (response) => this.fail(response, 'L’événement ne peut pas être annulé.'),
    });
  }
  protected sameDay(event: SchoolCalendarEvent): boolean {
    return new Date(event.startsAt).toDateString() === new Date(event.endsAt).toDateString();
  }
  private fail(response: { error?: { detail?: string } } | null, fallback: string): void {
    this.busy.set(false);
    this.error.set(response?.error?.detail ?? fallback);
  }
  private startOfMonth(value: Date): Date {
    return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth(), 1));
  }
  private emptyForm(): SchoolCalendarCommand {
    const start = new Date();
    start.setDate(start.getDate() + 7);
    start.setHours(9, 0, 0, 0);
    const end = new Date(start);
    end.setHours(10);
    return {
      title: '',
      description: '',
      eventType: 'MEETING',
      audienceScope: 'SCHOOL',
      startsAt: this.localInput(start),
      endsAt: this.localInput(end),
      allDay: false,
      location: '',
    };
  }
  private localInput(value: Date): string {
    const local = new Date(value.getTime() - value.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }
}
