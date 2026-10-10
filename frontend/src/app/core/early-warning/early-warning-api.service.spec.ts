import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EarlyWarningApiService } from './early-warning-api.service';

describe('EarlyWarningApiService', () => {
  let api: EarlyWarningApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EarlyWarningApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EarlyWarningApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the factual early warning dashboard', () => {
    api.dashboard('2026-08-10').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/early-warning/dashboard' &&
        candidate.params.get('asOf') === '2026-08-10',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('runs an idempotent evaluation', () => {
    api.evaluate('2026-08-10').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/early-warning/evaluate' &&
        candidate.params.get('asOf') === '2026-08-10',
    );
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('creates a configurable rule', () => {
    api
      .createRule({
        code: 'ABSENCE_15',
        name: 'Absences',
        description: 'Description factuelle',
        severity: 'MEDIUM',
        matchMode: 'ALL',
        active: true,
        conditions: [{ signalType: 'ABSENCE_RATE', operator: 'GT', threshold: 15 }],
      })
      .subscribe();
    const request = http.expectOne('/api/v1/early-warning/rules');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('acknowledges and resolves alerts without deleting them', () => {
    api.acknowledge('alert-1', 'Suivi planifié').subscribe();
    http.expectOne('/api/v1/early-warning/alerts/alert-1/acknowledge').flush({});
    api.resolve('alert-1', 'Suivi terminé').subscribe();
    http.expectOne('/api/v1/early-warning/alerts/alert-1/resolve').flush({});
  });
});
