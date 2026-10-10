import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HealthApiService } from './health-api.service';

describe('HealthApiService', () => {
  let service: HealthApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HealthApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the restricted health dashboard', () => {
    service.dashboard().subscribe();
    const request = http.expectOne('/api/v1/health/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('loads one sensitive student record', () => {
    service.student('student-1').subscribe();
    const request = http.expectOne('/api/v1/health/students/student-1');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('records a factual incident', () => {
    service.addIncident('student-1', { severity: 'LOW' }).subscribe();
    const request = http.expectOne('/api/v1/health/students/student-1/incidents');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ severity: 'LOW' });
    request.flush({});
  });
});
