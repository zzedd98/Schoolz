import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HumanResourcesApiService } from './human-resources-api.service';

describe('HumanResourcesApiService', () => {
  let api: HumanResourcesApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(HumanResourcesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the dated HR dashboard', () => {
    api.dashboard('2026-09-01').subscribe();
    const request = http.expectOne('/api/v1/hr/dashboard?date=2026-09-01');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('loads one employee record', () => {
    api.employee('employee-1').subscribe();
    const request = http.expectOne('/api/v1/hr/employees/employee-1');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('enforces the two-step leave decision routes', () => {
    api.managerReview('leave-1').subscribe();
    http
      .expectOne('/api/v1/hr/leave-requests/leave-1/manager-review')
      .flush({ status: 'MANAGER_REVIEW' });

    api.decideLeave('leave-1', 'APPROVED', 'Planning compatible').subscribe();
    const request = http.expectOne('/api/v1/hr/leave-requests/leave-1/decision');
    expect(request.request.body).toEqual({
      decision: 'APPROVED',
      comment: 'Planning compatible',
    });
    request.flush({ status: 'APPROVED' });
  });
});
