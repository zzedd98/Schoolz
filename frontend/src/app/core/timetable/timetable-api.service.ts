import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TimetableReferences {
  readonly campuses: readonly CampusOption[];
  readonly years: readonly YearOption[];
  readonly classrooms: readonly ClassroomOption[];
  readonly teachers: readonly TeacherOption[];
  readonly rooms: readonly RoomView[];
}

export interface CampusOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

export interface YearOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly status: string;
  readonly startsOn: string;
  readonly endsOn: string;
}

export interface ClassroomOption {
  readonly id: string;
  readonly campusId: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly capacity: number;
}

export interface TeacherOption {
  readonly id: string;
  readonly employeeNumber: string;
  readonly name: string;
}

export interface RoomEquipment {
  readonly code: string;
  readonly quantity: number;
}

export interface RoomView {
  readonly id: string;
  readonly campusId: string;
  readonly code: string;
  readonly name: string;
  readonly roomType: string;
  readonly capacity: number;
  readonly floorLabel: string | null;
  readonly equipment: readonly RoomEquipment[];
}

export interface SubjectView {
  readonly id: string;
  readonly code: string;
  readonly nameFr: string;
  readonly nameAr: string | null;
  readonly color: string;
  readonly active: boolean;
}

export interface ScheduleSummary {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  readonly validFrom: string;
  readonly validTo: string;
  readonly campusId: string;
  readonly campusName: string;
  readonly academicYearId: string;
  readonly academicYearName: string;
  readonly lessonCount: number;
}

export interface PeriodView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly sequenceNumber: number;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly teaching: boolean;
}

export interface TimeSlotView {
  readonly id: string;
  readonly periodId: string;
  readonly dayOfWeek: number;
  readonly sequenceNumber: number;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly teaching: boolean;
}

export interface AssignmentView {
  readonly id: string;
  readonly scheduleId: string;
  readonly classroomId: string;
  readonly classroomName: string;
  readonly subjectId: string;
  readonly subjectName: string;
  readonly color: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly weeklyPeriods: number;
  readonly requiredRoomType: string | null;
  readonly requiredEquipment: readonly string[];
  readonly active: boolean;
}

export interface LessonView {
  readonly id: string;
  readonly scheduleId: string;
  readonly assignmentId: string;
  readonly classroomId: string;
  readonly classroomName: string;
  readonly subjectId: string;
  readonly subjectName: string;
  readonly color: string;
  readonly teacherStaffId: string;
  readonly teacherName: string;
  readonly roomId: string;
  readonly roomName: string;
  readonly timeSlotId: string;
  readonly dayOfWeek: number;
  readonly sequenceNumber: number;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly title: string | null;
  readonly notes: string | null;
  readonly source: 'MANUAL' | 'OPTIMIZER';
}

export interface AvailabilityView {
  readonly targetType: 'TEACHER' | 'ROOM';
  readonly targetId: string;
  readonly timeSlotId: string;
  readonly availability: 'AVAILABLE' | 'UNAVAILABLE' | 'PREFERRED' | 'AVOID';
  readonly note: string | null;
}

export interface ConstraintView {
  readonly id: string;
  readonly constraintType: string;
  readonly scopeType: string;
  readonly targetId: string | null;
  readonly configuration: string;
  readonly hardConstraint: boolean;
  readonly weight: number;
  readonly enabled: boolean;
}

export interface TimetableBoard {
  readonly schedule: ScheduleSummary;
  readonly subjects: readonly SubjectView[];
  readonly periods: readonly PeriodView[];
  readonly timeSlots: readonly TimeSlotView[];
  readonly rooms: readonly RoomView[];
  readonly assignments: readonly AssignmentView[];
  readonly lessons: readonly LessonView[];
  readonly availabilities: readonly AvailabilityView[];
  readonly constraints: readonly ConstraintView[];
}

export interface TimetableConflict {
  readonly code: string;
  readonly severity: 'HARD' | 'SOFT';
  readonly message: string;
  readonly hard: boolean;
}

export interface PlacementCommand {
  readonly assignmentId: string;
  readonly roomId: string;
  readonly slotId: string;
  readonly title?: string;
  readonly notes?: string;
}

export interface PlacementValidation {
  readonly valid: boolean;
  readonly conflicts: readonly TimetableConflict[];
}

export interface OptimizationProposal {
  readonly assignmentId: string;
  readonly roomId: string;
  readonly slotId: string;
}

export interface OptimizationView {
  readonly proposals: readonly OptimizationProposal[];
  readonly unscheduled: Readonly<Record<string, number>>;
  readonly score: number;
}

@Injectable({ providedIn: 'root' })
export class TimetableApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/timetable';

  references(): Observable<TimetableReferences> {
    return this.http.get<TimetableReferences>(`${this.base}/references`);
  }

  subjects(): Observable<readonly SubjectView[]> {
    return this.http.get<readonly SubjectView[]>(`${this.base}/subjects`);
  }

  createSubject(command: {
    code: string;
    nameFr: string;
    nameAr?: string;
    color: string;
  }): Observable<SubjectView> {
    return this.http.post<SubjectView>(`${this.base}/subjects`, command);
  }

  schedules(): Observable<readonly ScheduleSummary[]> {
    return this.http.get<readonly ScheduleSummary[]>(`${this.base}/schedules`);
  }

  createSchedule(command: {
    campusId: string;
    academicYearId: string;
    code: string;
    name: string;
    validFrom: string;
    validTo: string;
    workingDays: readonly number[];
    periods: readonly {
      code: string;
      name: string;
      sequenceNumber: number;
      startsAt: string;
      endsAt: string;
      teaching: boolean;
    }[];
  }): Observable<TimetableBoard> {
    return this.http.post<TimetableBoard>(`${this.base}/schedules`, command);
  }

  board(scheduleId: string): Observable<TimetableBoard> {
    return this.http.get<TimetableBoard>(`${this.base}/schedules/${scheduleId}`);
  }

  addEquipment(roomId: string, equipmentCode: string, quantity = 1): Observable<RoomView> {
    return this.http.post<RoomView>(`${this.base}/rooms/${roomId}/equipment`, {
      equipmentCode,
      quantity,
    });
  }

  createAssignment(
    scheduleId: string,
    command: {
      classroomId: string;
      subjectId: string;
      teacherStaffId: string;
      weeklyPeriods: number;
      requiredRoomType?: string;
      requiredEquipment: readonly string[];
    },
  ): Observable<AssignmentView> {
    return this.http.post<AssignmentView>(
      `${this.base}/schedules/${scheduleId}/assignments`,
      command,
    );
  }

  setTeacherAvailability(
    scheduleId: string,
    teacherId: string,
    slotId: string,
    availability: string,
    note = '',
  ): Observable<AvailabilityView> {
    return this.http.put<AvailabilityView>(
      `${this.base}/schedules/${scheduleId}/teachers/${teacherId}/slots/${slotId}/availability`,
      { availability, note },
    );
  }

  setRoomAvailability(
    scheduleId: string,
    roomId: string,
    slotId: string,
    availability: string,
    note = '',
  ): Observable<AvailabilityView> {
    return this.http.put<AvailabilityView>(
      `${this.base}/schedules/${scheduleId}/rooms/${roomId}/slots/${slotId}/availability`,
      { availability, note },
    );
  }

  saveConstraint(
    scheduleId: string,
    command: {
      constraintType: string;
      scopeType: string;
      targetId?: string;
      configuration: Readonly<Record<string, unknown>>;
      hardConstraint: boolean;
      weight: number;
    },
  ): Observable<ConstraintView> {
    return this.http.post<ConstraintView>(
      `${this.base}/schedules/${scheduleId}/constraints`,
      command,
    );
  }

  validatePlacement(
    scheduleId: string,
    command: PlacementCommand,
  ): Observable<PlacementValidation> {
    return this.http.post<PlacementValidation>(
      `${this.base}/schedules/${scheduleId}/lessons/validate`,
      command,
    );
  }

  createLesson(scheduleId: string, command: PlacementCommand): Observable<LessonView> {
    return this.http.post<LessonView>(`${this.base}/schedules/${scheduleId}/lessons`, command);
  }

  moveLesson(
    scheduleId: string,
    lessonId: string,
    roomId: string,
    slotId: string,
  ): Observable<LessonView> {
    return this.http.patch<LessonView>(`${this.base}/schedules/${scheduleId}/lessons/${lessonId}`, {
      roomId,
      slotId,
    });
  }

  deleteLesson(scheduleId: string, lessonId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/schedules/${scheduleId}/lessons/${lessonId}`);
  }

  optimize(scheduleId: string): Observable<OptimizationView> {
    return this.http.post<OptimizationView>(
      `${this.base}/schedules/${scheduleId}/optimizer/proposals`,
      {},
    );
  }

  applyOptimization(
    scheduleId: string,
    proposals: readonly OptimizationProposal[],
  ): Observable<TimetableBoard> {
    return this.http.post<TimetableBoard>(`${this.base}/schedules/${scheduleId}/optimizer/apply`, {
      proposals,
    });
  }

  publish(scheduleId: string): Observable<TimetableBoard> {
    return this.http.post<TimetableBoard>(`${this.base}/schedules/${scheduleId}/publish`, {});
  }
}
