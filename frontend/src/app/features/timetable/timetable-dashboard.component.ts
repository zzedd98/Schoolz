import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { finalize, forkJoin } from 'rxjs';

import { LocaleService } from '../../core/i18n/locale.service';
import {
  AssignmentView,
  LessonView,
  OptimizationView,
  PlacementCommand,
  RoomView,
  ScheduleSummary,
  TimetableApiService,
  TimetableBoard,
  TimetableConflict,
  TimetableReferences,
  TimeSlotView,
} from '../../core/timetable/timetable-api.service';

type EditorMode = 'SCHEDULE' | 'SUBJECT' | 'ASSIGNMENT' | 'CONSTRAINT' | 'AVAILABILITY' | 'ROOM';
interface DragPayload {
  readonly kind: 'ASSIGNMENT' | 'LESSON';
  readonly id: string;
}

@Component({
  selector: 'app-timetable-dashboard',
  imports: [FormsModule, MatButtonModule],
  templateUrl: './timetable-dashboard.component.html',
  styleUrl: './timetable-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimetableDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(TimetableApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly references = signal<TimetableReferences | null>(null);
  protected readonly schedules = signal<readonly ScheduleSummary[]>([]);
  protected readonly board = signal<TimetableBoard | null>(null);
  protected readonly selectedScheduleId = signal('');
  protected readonly selectedClassroomId = signal('');
  protected readonly selectedTeacherId = signal('');
  protected readonly selectedRoomFilter = signal('');
  protected readonly targetRoomId = signal('');
  protected readonly selectedAssignmentId = signal('');
  protected readonly editor = signal<EditorMode | null>(null);
  protected readonly optimization = signal<OptimizationView | null>(null);
  protected readonly conflicts = signal<readonly TimetableConflict[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly dragged = signal<DragPayload | null>(null);

  protected newScheduleCampus = '';
  protected newScheduleYear = '';
  protected newScheduleCode = 'PRIMAIRE-2026';
  protected newScheduleName = 'Emploi du temps primaire';
  protected newScheduleFrom = '2026-09-01';
  protected newScheduleTo = '2027-06-30';
  protected newSubjectCode = '';
  protected newSubjectNameFr = '';
  protected newSubjectNameAr = '';
  protected newSubjectColor = '#0c3479';
  protected assignmentClassroom = '';
  protected assignmentSubject = '';
  protected assignmentTeacher = '';
  protected assignmentWeekly = 2;
  protected assignmentRoomType = '';
  protected assignmentEquipment = '';
  protected constraintTeacher = '';
  protected constraintType = 'MAX_HOURS_PER_DAY';
  protected constraintMaximum = 6;
  protected constraintHard = true;
  protected availabilityTargetType: 'TEACHER' | 'ROOM' = 'TEACHER';
  protected availabilityTarget = '';
  protected availabilitySlot = '';
  protected availabilityValue = 'UNAVAILABLE';
  protected availabilityNote = '';
  protected equipmentRoom = '';
  protected equipmentCode = '';

  protected readonly days = [1, 2, 3, 4, 5];

  constructor() {
    this.loadInitial();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected dayName(day: number): string {
    const french = ['', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];
    const arabic = ['', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت', 'الأحد'];
    return this.i18n.locale() === 'ar-MA' ? arabic[day] : french[day];
  }

  protected unscheduledCount(unscheduled: Readonly<Record<string, number>>): number {
    return Object.values(unscheduled).reduce((total, count) => total + count, 0);
  }

  protected selectSchedule(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.selectedScheduleId.set(id);
    this.loadBoard(id);
  }

  protected openEditor(mode: EditorMode): void {
    const refs = this.references();
    const board = this.board();
    if (mode === 'SCHEDULE' && refs) {
      const year = refs.years.find((value) => value.status === 'ACTIVE') ?? refs.years[0];
      this.newScheduleCampus ||= refs.campuses[0]?.id ?? '';
      this.newScheduleYear ||= year?.id ?? '';
      this.newScheduleFrom = year?.startsOn ?? this.newScheduleFrom;
      this.newScheduleTo = year?.endsOn ?? this.newScheduleTo;
    }
    if (mode === 'ASSIGNMENT' && refs && board) {
      this.assignmentClassroom ||=
        this.selectedClassroomId() ||
        refs.classrooms.find((value) => value.campusId === board.schedule.campusId)?.id ||
        '';
      this.assignmentSubject ||= board.subjects[0]?.id ?? '';
      this.assignmentTeacher ||= refs.teachers[0]?.id ?? '';
    }
    if (mode === 'CONSTRAINT' && refs) this.constraintTeacher ||= refs.teachers[0]?.id ?? '';
    if (mode === 'AVAILABILITY' && board && refs) {
      this.availabilityTarget ||= refs.teachers[0]?.id ?? '';
      this.availabilitySlot ||= board.timeSlots.find((slot) => slot.teaching)?.id ?? '';
    }
    if (mode === 'ROOM' && board) this.equipmentRoom ||= board.rooms[0]?.id ?? '';
    this.error.set('');
    this.editor.set(mode);
  }

  protected closeEditor(): void {
    this.editor.set(null);
  }

  protected createSchedule(): void {
    if (!this.newScheduleCampus || !this.newScheduleYear) return;
    const periods = [
      ['P1', '08:00', '09:00'],
      ['P2', '09:00', '10:00'],
      ['P3', '10:15', '11:15'],
      ['P4', '11:15', '12:15'],
      ['P5', '14:00', '15:00'],
      ['P6', '15:00', '16:00'],
    ].map(([code, startsAt, endsAt], index) => ({
      code,
      name: `${startsAt}–${endsAt}`,
      sequenceNumber: index + 1,
      startsAt,
      endsAt,
      teaching: true,
    }));
    this.run(
      this.api.createSchedule({
        campusId: this.newScheduleCampus,
        academicYearId: this.newScheduleYear,
        code: this.newScheduleCode,
        name: this.newScheduleName,
        validFrom: this.newScheduleFrom,
        validTo: this.newScheduleTo,
        workingDays: this.days,
        periods,
      }),
      (board) => {
        this.board.set(board);
        this.selectedScheduleId.set(board.schedule.id);
        this.editor.set(null);
        this.refreshSchedules();
        this.success(this.text('Planning créé.', 'تم إنشاء الجدول.'));
      },
    );
  }

  protected createSubject(): void {
    this.run(
      this.api.createSubject({
        code: this.newSubjectCode,
        nameFr: this.newSubjectNameFr,
        nameAr: this.newSubjectNameAr,
        color: this.newSubjectColor,
      }),
      () => {
        this.editor.set(null);
        this.newSubjectCode = '';
        this.newSubjectNameFr = '';
        this.reloadBoard(this.text('Matière créée.', 'تم إنشاء المادة.'));
      },
    );
  }

  protected createAssignment(): void {
    const scheduleId = this.selectedScheduleId();
    if (!scheduleId) return;
    this.run(
      this.api.createAssignment(scheduleId, {
        classroomId: this.assignmentClassroom,
        subjectId: this.assignmentSubject,
        teacherStaffId: this.assignmentTeacher,
        weeklyPeriods: Number(this.assignmentWeekly),
        requiredRoomType: this.assignmentRoomType || undefined,
        requiredEquipment: this.csv(this.assignmentEquipment),
      }),
      () => {
        this.editor.set(null);
        this.reloadBoard(this.text('Affectation créée.', 'تم إنشاء التكليف.'));
      },
    );
  }

  protected saveConstraint(): void {
    const scheduleId = this.selectedScheduleId();
    if (!scheduleId || !this.constraintTeacher) return;
    this.run(
      this.api.saveConstraint(scheduleId, {
        constraintType: this.constraintType,
        scopeType: 'TEACHER',
        targetId: this.constraintTeacher,
        configuration: { maxPeriods: Number(this.constraintMaximum) },
        hardConstraint: this.constraintHard,
        weight: 100,
      }),
      () => {
        this.editor.set(null);
        this.reloadBoard(this.text('Contrainte enregistrée.', 'تم حفظ القيد.'));
      },
    );
  }

  protected saveAvailability(): void {
    const scheduleId = this.selectedScheduleId();
    if (!scheduleId || !this.availabilityTarget || !this.availabilitySlot) return;
    const request =
      this.availabilityTargetType === 'TEACHER'
        ? this.api.setTeacherAvailability(
            scheduleId,
            this.availabilityTarget,
            this.availabilitySlot,
            this.availabilityValue,
            this.availabilityNote,
          )
        : this.api.setRoomAvailability(
            scheduleId,
            this.availabilityTarget,
            this.availabilitySlot,
            this.availabilityValue,
            this.availabilityNote,
          );
    this.run(request, () => {
      this.editor.set(null);
      this.reloadBoard(this.text('Disponibilité enregistrée.', 'تم حفظ التوفر.'));
    });
  }

  protected availabilityTypeChanged(): void {
    const board = this.board();
    const refs = this.references();
    this.availabilityTarget =
      this.availabilityTargetType === 'TEACHER'
        ? (refs?.teachers[0]?.id ?? '')
        : (board?.rooms[0]?.id ?? '');
  }

  protected addEquipment(): void {
    if (!this.equipmentRoom || !this.equipmentCode) return;
    this.run(this.api.addEquipment(this.equipmentRoom, this.equipmentCode), () => {
      this.editor.set(null);
      this.equipmentCode = '';
      this.reloadBoard(this.text('Équipement ajouté.', 'تمت إضافة التجهيز.'));
    });
  }

  protected startDrag(kind: DragPayload['kind'], id: string, event: DragEvent): void {
    if (!this.isEditable()) return;
    const payload = { kind, id } as const;
    this.dragged.set(payload);
    event.dataTransfer?.setData('text/plain', JSON.stringify(payload));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  protected allowDrop(event: DragEvent): void {
    if (!this.isEditable()) return;
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
  }

  protected drop(slot: TimeSlotView, event: DragEvent): void {
    event.preventDefault();
    if (!slot.teaching || !this.isEditable()) return;
    let payload = this.dragged();
    if (!payload && event.dataTransfer?.getData('text/plain')) {
      try {
        payload = JSON.parse(event.dataTransfer.getData('text/plain')) as DragPayload;
      } catch {
        payload = null;
      }
    }
    this.dragged.set(null);
    if (!payload) return;
    this.place(payload, slot);
  }

  protected selectAssignment(assignmentId: string): void {
    if (!this.isEditable()) return;
    this.selectedAssignmentId.set(this.selectedAssignmentId() === assignmentId ? '' : assignmentId);
  }

  protected placeSelected(slot: TimeSlotView): void {
    const assignmentId = this.selectedAssignmentId();
    if (!assignmentId || !slot.teaching) return;
    this.place({ kind: 'ASSIGNMENT', id: assignmentId }, slot);
  }

  protected deleteLesson(lesson: LessonView): void {
    if (!this.isEditable()) return;
    this.run(this.api.deleteLesson(lesson.scheduleId, lesson.id), () => {
      this.reloadBoard(this.text('Cours retiré.', 'تم حذف الحصة.'));
    });
  }

  protected proposeOptimization(): void {
    const scheduleId = this.selectedScheduleId();
    if (!scheduleId || !this.isEditable()) return;
    this.run(this.api.optimize(scheduleId), (optimization) => {
      this.optimization.set(optimization);
      this.conflicts.set([]);
    });
  }

  protected applyOptimization(): void {
    const scheduleId = this.selectedScheduleId();
    const optimization = this.optimization();
    if (!scheduleId || !optimization?.proposals.length) return;
    this.run(this.api.applyOptimization(scheduleId, optimization.proposals), (board) => {
      this.board.set(board);
      this.optimization.set(null);
      this.success(this.text('Propositions appliquées.', 'تم تطبيق المقترحات.'));
      this.refreshSchedules();
    });
  }

  protected publish(): void {
    const scheduleId = this.selectedScheduleId();
    if (!scheduleId || !this.isEditable()) return;
    this.run(this.api.publish(scheduleId), (board) => {
      this.board.set(board);
      this.success(this.text('Planning publié et verrouillé.', 'تم نشر الجدول وقفله.'));
      this.refreshSchedules();
    });
  }

  protected filteredAssignments(): readonly AssignmentView[] {
    const board = this.board();
    if (!board) return [];
    return board.assignments.filter(
      (assignment) =>
        (!this.selectedClassroomId() || assignment.classroomId === this.selectedClassroomId()) &&
        (!this.selectedTeacherId() || assignment.teacherStaffId === this.selectedTeacherId()),
    );
  }

  protected lessons(slotId: string): readonly LessonView[] {
    const board = this.board();
    if (!board) return [];
    return board.lessons.filter(
      (lesson) =>
        lesson.timeSlotId === slotId &&
        (!this.selectedClassroomId() || lesson.classroomId === this.selectedClassroomId()) &&
        (!this.selectedTeacherId() || lesson.teacherStaffId === this.selectedTeacherId()) &&
        (!this.selectedRoomFilter() || lesson.roomId === this.selectedRoomFilter()),
    );
  }

  protected slot(day: number, periodId: string): TimeSlotView | undefined {
    return this.board()?.timeSlots.find(
      (value) => value.dayOfWeek === day && value.periodId === periodId,
    );
  }

  protected placedCount(assignmentId: string): number {
    return (
      this.board()?.lessons.filter((lesson) => lesson.assignmentId === assignmentId).length ?? 0
    );
  }

  protected remaining(assignment: AssignmentView): number {
    return Math.max(0, assignment.weeklyPeriods - this.placedCount(assignment.id));
  }

  protected isEditable(): boolean {
    return this.board()?.schedule.status === 'DRAFT';
  }

  protected closeConflict(): void {
    this.conflicts.set([]);
  }

  protected closeOptimization(): void {
    this.optimization.set(null);
  }

  protected teacherName(id: string): string {
    return this.references()?.teachers.find((teacher) => teacher.id === id)?.name ?? '—';
  }

  protected slotLabel(slotId: string): string {
    const slot = this.board()?.timeSlots.find((value) => value.id === slotId);
    return slot ? `${this.dayName(slot.dayOfWeek)} · ${slot.startsAt.slice(0, 5)}` : '—';
  }

  protected assignmentLabel(id: string): string {
    const value = this.board()?.assignments.find((assignment) => assignment.id === id);
    return value ? `${value.subjectName} · ${value.classroomName}` : '—';
  }

  private place(payload: DragPayload, slot: TimeSlotView): void {
    const board = this.board();
    if (!board) return;
    const lesson =
      payload.kind === 'LESSON'
        ? board.lessons.find((value) => value.id === payload.id)
        : undefined;
    const assignment = board.assignments.find(
      (value) => value.id === (lesson?.assignmentId ?? payload.id),
    );
    if (!assignment) return;
    const roomId = this.targetRoomId() || lesson?.roomId || this.suggestRoom(assignment)?.id;
    if (!roomId) {
      this.error.set(this.text('Aucune salle disponible.', 'لا توجد قاعة متاحة.'));
      return;
    }
    const command: PlacementCommand = {
      assignmentId: assignment.id,
      roomId,
      slotId: slot.id,
      title: assignment.subjectName,
    };
    this.busy.set(true);
    this.api
      .validatePlacement(board.schedule.id, command)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (validation) => {
          if (!validation.valid) {
            this.busy.set(false);
            this.conflicts.set(validation.conflicts);
            return;
          }
          const mutation = lesson
            ? this.api.moveLesson(board.schedule.id, lesson.id, roomId, slot.id)
            : this.api.createLesson(board.schedule.id, command);
          mutation
            .pipe(
              finalize(() => this.busy.set(false)),
              takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
              next: () => {
                this.selectedAssignmentId.set('');
                this.reloadBoard(this.text('Placement enregistré.', 'تم حفظ الحصة.'));
              },
              error: (error: HttpErrorResponse) => this.fail(error),
            });
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          this.fail(error);
        },
      });
  }

  private suggestRoom(assignment: AssignmentView): RoomView | undefined {
    const board = this.board();
    const refs = this.references();
    const classroom = refs?.classrooms.find((value) => value.id === assignment.classroomId);
    if (!board) return undefined;
    return (
      board.rooms.find(
        (room) =>
          room.capacity >= (classroom?.capacity ?? 0) &&
          (!assignment.requiredRoomType || room.roomType === assignment.requiredRoomType) &&
          assignment.requiredEquipment.every((required) =>
            room.equipment.some((equipment) => equipment.code === required),
          ),
      ) ?? board.rooms[0]
    );
  }

  private loadInitial(): void {
    this.busy.set(true);
    forkJoin({ references: this.api.references(), schedules: this.api.schedules() })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ references, schedules }) => {
          this.references.set(references);
          this.schedules.set(schedules);
          const first = schedules[0];
          if (first) {
            this.selectedScheduleId.set(first.id);
            this.loadBoard(first.id);
          }
        },
        error: (error: HttpErrorResponse) => this.fail(error),
      });
  }

  private loadBoard(scheduleId: string): void {
    if (!scheduleId) return;
    this.busy.set(true);
    this.api
      .board(scheduleId)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (board) => {
          this.board.set(board);
          this.selectedClassroomId.set(
            this.selectedClassroomId() || board.assignments[0]?.classroomId || '',
          );
          this.targetRoomId.set(board.rooms[0]?.id ?? '');
        },
        error: (error: HttpErrorResponse) => this.fail(error),
      });
  }

  private reloadBoard(message: string): void {
    const id = this.selectedScheduleId();
    if (id) this.loadBoard(id);
    this.success(message);
    this.refreshSchedules();
  }

  private refreshSchedules(): void {
    this.api
      .schedules()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((schedules) => this.schedules.set(schedules));
  }

  private run<T>(request: import('rxjs').Observable<T>, next: (value: T) => void): void {
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next, error: (error: HttpErrorResponse) => this.fail(error) });
  }

  private csv(value: string): readonly string[] {
    return value
      .split(',')
      .map((item) => item.trim().toUpperCase())
      .filter(Boolean);
  }

  private success(message: string): void {
    this.message.set(message);
    this.error.set('');
  }

  private fail(error: HttpErrorResponse): void {
    const body = error.error as {
      message?: string;
      conflicts?: readonly TimetableConflict[];
    } | null;
    if (body?.conflicts?.length) this.conflicts.set(body.conflicts);
    this.error.set(body?.message ?? this.text('Une erreur est survenue.', 'حدث خطأ غير متوقع.'));
  }
}
