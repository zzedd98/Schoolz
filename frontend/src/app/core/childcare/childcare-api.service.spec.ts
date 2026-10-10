import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ChildcareApiService } from './childcare-api.service';

describe('ChildcareApiService', () => {
  let service: ChildcareApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChildcareApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the educator dashboard for one group and date', () => {
    service.dashboard('2026-08-08', 'group-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/childcare/dashboard' &&
        candidate.params.get('date') === '2026-08-08' &&
        candidate.params.get('groupId') === 'group-1',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ date: '2026-08-08', groups: [], children: [], policy: {} });
  });

  it('records a one-tap meal event', () => {
    service.addEvent('student-1', { eventType: 'MEAL', outcome: 'COMPLETED' }).subscribe();
    const request = http.expectOne('/api/v1/childcare/children/student-1/events');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ eventType: 'MEAL', outcome: 'COMPLETED' });
    request.flush({});
  });

  it('loads published parent reports with the portal cookie', () => {
    service.reports('2026-08-08').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/public/childcare/reports' &&
        candidate.params.get('date') === '2026-08-08',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush([]);
  });
});
