import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TimetableApiService } from './timetable-api.service';

describe('TimetableApiService', () => {
  let service: TimetableApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TimetableApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the server to validate a drop before writing', () => {
    const placement = { assignmentId: 'assignment-1', roomId: 'room-1', slotId: 'slot-1' };
    service.validatePlacement('schedule-1', placement).subscribe();
    const request = http.expectOne('/api/v1/timetable/schedules/schedule-1/lessons/validate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(placement);
    request.flush({ valid: true, conflicts: [] });
  });

  it('moves a lesson with room and slot identifiers only', () => {
    service.moveLesson('schedule-1', 'lesson-1', 'room-2', 'slot-3').subscribe();
    const request = http.expectOne('/api/v1/timetable/schedules/schedule-1/lessons/lesson-1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ roomId: 'room-2', slotId: 'slot-3' });
    request.flush({});
  });

  it('keeps optimization preview and application as separate calls', () => {
    service.optimize('schedule-1').subscribe();
    const preview = http.expectOne('/api/v1/timetable/schedules/schedule-1/optimizer/proposals');
    expect(preview.request.method).toBe('POST');
    preview.flush({ proposals: [], unscheduled: {}, score: 0 });

    const proposals = [{ assignmentId: 'assignment-1', roomId: 'room-1', slotId: 'slot-1' }];
    service.applyOptimization('schedule-1', proposals).subscribe();
    const apply = http.expectOne('/api/v1/timetable/schedules/schedule-1/optimizer/apply');
    expect(apply.request.body).toEqual({ proposals });
    apply.flush({});
  });

  it('persists a configurable teacher limit', () => {
    service
      .saveConstraint('schedule-1', {
        constraintType: 'MAX_HOURS_PER_DAY',
        scopeType: 'TEACHER',
        targetId: 'teacher-1',
        configuration: { maxPeriods: 6 },
        hardConstraint: true,
        weight: 100,
      })
      .subscribe();
    const request = http.expectOne('/api/v1/timetable/schedules/schedule-1/constraints');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.configuration.maxPeriods).toBe(6);
    request.flush({});
  });
});
