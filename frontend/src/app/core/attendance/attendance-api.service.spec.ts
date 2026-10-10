import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AttendanceApiService } from './attendance-api.service';

describe('AttendanceApiService', () => {
  let service: AttendanceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AttendanceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('saves the teacher call in one batch', () => {
    service.updateRecords('session-1', [{ recordId: 'record-1', status: 'ABSENT' }]).subscribe();
    const request = http.expectOne('/api/v1/attendance/sessions/session-1/records');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.records).toEqual([{ recordId: 'record-1', status: 'ABSENT' }]);
    request.flush({});
  });

  it('submits a parent justification with the portal cookie', () => {
    service
      .justify('absence-1', { reasonId: 'reason-1', statement: 'Consultation médicale.' })
      .subscribe();
    const request = http.expectOne('/api/v1/public/attendance/absences/absence-1/justifications');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });

  it('loads filtered attendance statistics', () => {
    service.statistics('2026-08-01', '2026-08-08', 'class-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/attendance/statistics' &&
        candidate.params.get('from') === '2026-08-01' &&
        candidate.params.get('to') === '2026-08-08' &&
        candidate.params.get('classroomId') === 'class-1',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
