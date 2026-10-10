import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { IntegrationApiService } from './integration-api.service';

describe('IntegrationApiService', () => {
  let api: IntegrationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(IntegrationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the integration dashboard', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/integrations/dashboard').flush({});
  });

  it('creates a typed export', () => {
    api.export('massar-file', 'STUDENTS', 'CSV').subscribe();
    const request = http.expectOne('/api/v1/integrations/massar-file/exports');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ dataset: 'STUDENTS', format: 'CSV' });
    request.flush({});
  });

  it('uploads a file with its dataset', () => {
    const file = new File(['student_identifier\nSZ-01'], 'eleves.csv', { type: 'text/csv' });
    api.import('massar-file', 'STUDENTS', file).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/integrations/massar-file/imports' &&
        candidate.params.get('dataset') === 'STUDENTS',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    request.flush({});
  });

  it('downloads the exact generated file', () => {
    api.download('job-1').subscribe();
    const request = http.expectOne('/api/v1/integrations/jobs/job-1/file');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['SchoolZ']));
  });
});
