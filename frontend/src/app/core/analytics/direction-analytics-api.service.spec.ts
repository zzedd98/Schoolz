import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DirectionAnalyticsApiService } from './direction-analytics-api.service';

describe('DirectionAnalyticsApiService', () => {
  let api: DirectionAnalyticsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DirectionAnalyticsApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DirectionAnalyticsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the group direction cockpit at a selected date', () => {
    api.direction('2026-08-10').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/analytics/direction' &&
        candidate.params.get('asOf') === '2026-08-10' &&
        !candidate.params.has('campusId'),
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('filters every indicator by campus', () => {
    api.direction('2026-08-10', 'campus-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/analytics/direction' &&
        candidate.params.get('campusId') === 'campus-1',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
