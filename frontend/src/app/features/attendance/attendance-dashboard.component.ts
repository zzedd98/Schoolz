import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { Observable, finalize, forkJoin, of, switchMap } from 'rxjs';

import {
  AttendanceApiService,
  AttendanceJustification,
  AttendanceRecord,
  AttendanceReferences,
  AttendanceSession,
  AttendanceSessionSummary,
  AttendanceStatistics,
  AttendanceStatus,
  RecordCommand,
} from '../../core/attendance/attendance-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { LocaleService } from '../../core/i18n/locale.service';

interface RecordDraft {
  readonly status: AttendanceStatus;
  readonly arrivedAt: string;
  readonly leftAt: string;
  readonly notes: string;
  readonly departureReason: string;
  readonly pickupPersonName: string;
}

@Component({
  selector: 'app-attendance-dashboard',
  imports: [FormsModule, MatButtonModule],
  templateUrl: './attendance-dashboard.component.html',
  styleUrl: './attendance-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendanceDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(AttendanceApiService);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly date = signal(
    this.route.snapshot.queryParamMap.get('date') ?? this.localDate(new Date()),
  );
  protected readonly references = signal<AttendanceReferences | null>(null);
  protected readonly sessions = signal<readonly AttendanceSessionSummary[]>([]);
  protected readonly activeSession = signal<AttendanceSession | null>(null);
  protected readonly drafts = signal<Record<string, RecordDraft>>({});
  protected readonly statistics = signal<AttendanceStatistics | null>(null);
  protected readonly justifications = signal<readonly AttendanceJustification[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly createOpen = signal(false);
  protected readonly studentEditor = signal<{
    record: AttendanceRecord;
    action: 'INCIDENT' | 'OBSERVATION';
  } | null>(null);

  protected newClassroom = '';
  protected newTeacher = '';
  protected newSubjectCode = 'MATH';
  protected newSubjectName = 'Mathématiques';
  protected newStartsAt = '08:00';
  protected newEndsAt = '09:00';
  protected reviewNotes = '';
  protected schoolLifeCategory = 'BEHAVIOUR';
  protected schoolLifeSeverity = 'LOW';
  protected schoolLifeText = '';
  protected schoolLifeVisible = true;
  protected addWarning = false;
  private portalContextApplied = false;

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected selectDate(event: Event): void {
    this.date.set((event.target as HTMLInputElement).value);
    this.activeSession.set(null);
    this.load();
  }

  protected open(sessionId: string): void {
    this.busy.set(true);
    this.api
      .session(sessionId)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (session) => this.acceptSession(session),
        error: () => this.fail(this.text("Impossible d'ouvrir l'appel.", 'تعذر فتح سجل الحضور.')),
      });
  }

  protected closeCall(): void {
    this.activeSession.set(null);
  }

  protected status(recordId: string): AttendanceStatus {
    return this.drafts()[recordId]?.status ?? 'PRESENT';
  }

  protected setStatus(recordId: string, status: AttendanceStatus): void {
    const current = this.drafts()[recordId];
    if (!current) return;
    const session = this.activeSession();
    this.drafts.update((drafts) => ({
      ...drafts,
      [recordId]: {
        ...current,
        status,
        arrivedAt:
          status === 'LATE'
            ? current.arrivedAt || this.minuteAfter(session?.startsAt ?? '08:00', 10)
            : current.arrivedAt,
        leftAt:
          status === 'EARLY_DEPARTURE'
            ? current.leftAt || this.localTime(new Date())
            : current.leftAt,
      },
    }));
  }

  protected updateDraft(recordId: string, field: keyof RecordDraft, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.drafts.update((drafts) => ({
      ...drafts,
      [recordId]: { ...drafts[recordId], [field]: value },
    }));
  }

  protected saveCall(): void {
    const session = this.activeSession();
    if (!session || session.status !== 'OPEN') return;
    const records: RecordCommand[] = session.records.map((record) => {
      const draft = this.drafts()[record.id];
      return {
        recordId: record.id,
        status: draft.status,
        notes: draft.notes || undefined,
        arrivedAt: draft.status === 'LATE' ? draft.arrivedAt : undefined,
        leftAt: draft.status === 'EARLY_DEPARTURE' ? draft.leftAt : undefined,
        departureReason:
          draft.status === 'EARLY_DEPARTURE' ? draft.departureReason || undefined : undefined,
        pickupPersonName:
          draft.status === 'EARLY_DEPARTURE' ? draft.pickupPersonName || undefined : undefined,
      };
    });
    this.mutateSession(
      this.api.updateRecords(session.id, records),
      this.text('Appel enregistré.', 'تم حفظ الحضور.'),
    );
  }

  protected submitCall(): void {
    const session = this.activeSession();
    if (!session || session.status !== 'OPEN') return;
    this.mutateSession(
      this.api
        .updateRecords(
          session.id,
          session.records.map((record) => {
            const draft = this.drafts()[record.id];
            return {
              recordId: record.id,
              status: draft.status,
              notes: draft.notes || undefined,
              arrivedAt: draft.status === 'LATE' ? draft.arrivedAt : undefined,
              leftAt: draft.status === 'EARLY_DEPARTURE' ? draft.leftAt : undefined,
              departureReason: draft.departureReason || undefined,
              pickupPersonName: draft.pickupPersonName || undefined,
            };
          }),
        )
        .pipe(switchMap(() => this.api.submit(session.id))),
      this.text('Appel clôturé et parents notifiés.', 'تم إغلاق الحضور وإشعار الوالدين.'),
    );
  }

  protected openCreate(): void {
    const references = this.references();
    this.newClassroom = references?.classrooms[0]?.id ?? '';
    this.newTeacher = references?.teachers[0]?.id ?? '';
    this.createOpen.set(true);
  }

  protected createSession(): void {
    if (!this.newClassroom || !this.newTeacher) return;
    this.busy.set(true);
    this.api
      .createSession({
        classroomId: this.newClassroom,
        teacherStaffId: this.newTeacher,
        sessionDate: this.date(),
        subjectCode: this.newSubjectCode,
        subjectName: this.newSubjectName,
        startsAt: this.newStartsAt,
        endsAt: this.newEndsAt,
      })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (session) => {
          this.acceptSession(session);
          this.createOpen.set(false);
          this.message.set(
            this.text(
              'Session créée, tous présents par défaut.',
              'تم إنشاء الحصة، الجميع حاضر افتراضياً.',
            ),
          );
          this.reloadSummary();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.fail(
            response.error?.detail ?? this.text('Création impossible.', 'تعذر إنشاء الحصة.'),
          ),
      });
  }

  protected review(item: AttendanceJustification, decision: 'APPROVED' | 'REJECTED'): void {
    this.busy.set(true);
    this.api
      .reviewJustification(item.id, decision, this.reviewNotes)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.justifications.update((items) => items.filter((current) => current.id !== item.id));
          this.reviewNotes = '';
          this.message.set(
            decision === 'APPROVED'
              ? this.text('Justification acceptée.', 'تم قبول التبرير.')
              : this.text('Justification refusée.', 'تم رفض التبرير.'),
          );
          this.reloadStatistics();
        },
        error: () => this.fail(this.text('Décision impossible.', 'تعذر اتخاذ القرار.')),
      });
  }

  protected openStudentEditor(record: AttendanceRecord, action: 'INCIDENT' | 'OBSERVATION'): void {
    this.studentEditor.set({ record, action });
    this.schoolLifeCategory = action === 'INCIDENT' ? 'BEHAVIOUR' : 'PROGRESS';
    this.schoolLifeSeverity = 'LOW';
    this.schoolLifeText = '';
    this.schoolLifeVisible = true;
    this.addWarning = false;
  }

  protected submitSchoolLife(): void {
    const editor = this.studentEditor();
    const session = this.activeSession();
    if (!editor || !session || !this.schoolLifeText.trim()) return;
    this.busy.set(true);
    const request =
      editor.action === 'OBSERVATION'
        ? this.api.createObservation({
            studentId: editor.record.studentId,
            classroomId: session.classroomId,
            teacherStaffId: session.teacherStaffId,
            observationDate: this.date(),
            category: this.schoolLifeCategory,
            observation: this.schoolLifeText,
            visibleToParent: this.schoolLifeVisible,
          })
        : this.api
            .createIncident({
              studentId: editor.record.studentId,
              classroomId: session.classroomId,
              category: this.schoolLifeCategory,
              severity: this.schoolLifeSeverity,
              description: this.schoolLifeText,
            })
            .pipe(
              switchMap((incident) =>
                this.addWarning
                  ? this.api.createSanction(incident.id, {
                      sanctionType: 'WARNING',
                      description: this.text('Avertissement formel.', 'إنذار رسمي.'),
                      startsOn: this.date(),
                    })
                  : new Observable<unknown>((subscriber) => {
                      subscriber.next(incident);
                      subscriber.complete();
                    }),
              ),
            );
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.studentEditor.set(null);
          this.message.set(
            editor.action === 'OBSERVATION'
              ? this.text('Observation enregistrée.', 'تم تسجيل الملاحظة.')
              : this.text('Incident enregistré.', 'تم تسجيل الحادث.'),
          );
        },
        error: () => this.fail(this.text('Enregistrement impossible.', 'تعذر الحفظ.')),
      });
  }

  protected count(status: AttendanceStatus): number {
    return Object.values(this.drafts()).filter((draft) => draft.status === status).length;
  }

  protected statusLabel(status: AttendanceStatus): string {
    const labels: Record<AttendanceStatus, string> = {
      PRESENT: this.text('Présent', 'حاضر'),
      ABSENT: this.text('Absent', 'غائب'),
      LATE: this.text('Retard', 'متأخر'),
      EARLY_DEPARTURE: this.text('Départ', 'مغادرة'),
    };
    return labels[status];
  }

  private load(): void {
    this.busy.set(true);
    const from = this.localDate(new Date(Date.now() - 30 * 86400000));
    forkJoin({
      references: this.api.references(),
      sessions: this.api.sessions(this.date()),
      statistics: this.api.statistics(from, this.date()),
      justifications: this.auth.hasPermission('attendance.manage')
        ? this.api.justifications()
        : of([] as readonly AttendanceJustification[]),
    })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => {
          this.references.set(data.references);
          this.sessions.set(data.sessions);
          this.statistics.set(data.statistics);
          this.justifications.set(data.justifications);
          this.applyPortalContext();
        },
        error: () =>
          this.fail(this.text('La vie scolaire est indisponible.', 'الحياة المدرسية غير متاحة.')),
      });
  }

  private applyPortalContext(): void {
    if (this.portalContextApplied) return;
    const params = this.route.snapshot.queryParamMap;
    const classroomId = params.get('classroomId');
    const teacherStaffId = params.get('teacherStaffId');
    if (!classroomId || !teacherStaffId) return;
    this.portalContextApplied = true;
    this.newClassroom = classroomId;
    this.newTeacher = teacherStaffId;
    this.newSubjectCode = params.get('subjectCode') ?? this.newSubjectCode;
    this.newSubjectName = params.get('subjectName') ?? this.newSubjectName;
    this.newStartsAt = (params.get('startsAt') ?? this.newStartsAt).slice(0, 5);
    this.newEndsAt = (params.get('endsAt') ?? this.newEndsAt).slice(0, 5);
    const sessionId = params.get('sessionId');
    if (sessionId) this.open(sessionId);
    else this.createOpen.set(true);
  }

  private acceptSession(session: AttendanceSession): void {
    this.activeSession.set(session);
    this.drafts.set(
      Object.fromEntries(
        session.records.map((record) => [
          record.id,
          {
            status: record.status,
            arrivedAt: record.arrivedAt?.slice(0, 5) ?? '',
            leftAt: record.leftAt?.slice(0, 5) ?? '',
            notes: record.notes ?? '',
            departureReason: record.departureReason ?? '',
            pickupPersonName: record.pickupPersonName ?? '',
          },
        ]),
      ),
    );
  }

  private mutateSession(request: Observable<AttendanceSession>, success: string): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (session) => {
          this.acceptSession(session);
          this.message.set(success);
          this.reloadSummary();
          this.reloadStatistics();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.fail(
            response.error?.detail ??
              this.text("L'appel n'a pas été enregistré.", 'لم يتم حفظ الحضور.'),
          ),
      });
  }

  private reloadSummary(): void {
    this.api
      .sessions(this.date())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((sessions) => this.sessions.set(sessions));
  }

  private reloadStatistics(): void {
    const from = this.localDate(new Date(Date.now() - 30 * 86400000));
    this.api
      .statistics(from, this.date())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((statistics) => this.statistics.set(statistics));
  }

  private fail(message: string): void {
    this.error.set(message);
  }

  private minuteAfter(time: string, minutes: number): string {
    const [hour, minute] = time.slice(0, 5).split(':').map(Number);
    const value = hour * 60 + minute + minutes;
    return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private localTime(date: Date): string {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }
}
