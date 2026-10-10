import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PayrollApiService } from './payroll-api.service';

describe('PayrollApiService', () => {
  let api: PayrollApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PayrollApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the payroll cockpit', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/payroll/dashboard').flush({});
  });

  it('calculates and finalizes a run', () => {
    api.calculate('run-1').subscribe();
    http.expectOne('/api/v1/payroll/runs/run-1/calculate').flush({});
    api.finalize('run-1').subscribe();
    http.expectOne('/api/v1/payroll/runs/run-1/finalize').flush({});
  });

  it('requests a signed accounting export', () => {
    api.accountingExport('run-1', 'CSV').subscribe();
    const request = http.expectOne(
      (candidate) => candidate.url === '/api/v1/payroll/runs/run-1/accounting-export',
    );
    expect(request.request.params.get('format')).toBe('CSV');
    request.flush({});
  });
});
