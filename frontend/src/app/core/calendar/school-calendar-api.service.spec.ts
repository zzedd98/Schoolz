import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SchoolCalendarApiService } from './school-calendar-api.service';

describe('SchoolCalendarApiService', () => {
  let service: SchoolCalendarApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SchoolCalendarApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads a filtered range', () => {
    service
      .calendar('2026-08-01T00:00:00Z', '2026-10-01T00:00:00Z', 'EXAM', 'campus-1')
      .subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/calendar' && candidate.params.get('type') === 'EXAM',
    );
    expect(request.request.params.get('campusId')).toBe('campus-1');
    request.flush({});
  });

  it('creates and soft-cancels an event', () => {
    service
      .create({
        title: 'Réunion',
        description: '',
        eventType: 'MEETING',
        audienceScope: 'SCHOOL',
        startsAt: '2026-09-01T09:00:00Z',
        endsAt: '2026-09-01T10:00:00Z',
        allDay: false,
        location: '',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/calendar').request.method).toBe('POST');
    service.cancel('event-1').subscribe();
    expect(http.expectOne('/api/v1/calendar/event-1/cancel').request.method).toBe('PUT');
  });
});
