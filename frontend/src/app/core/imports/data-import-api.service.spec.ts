import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DataImportApiService } from './data-import-api.service';

describe('DataImportApiService', () => {
  let service: DataImportApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DataImportApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads a CSV as multipart data for the selected business type', () => {
    const file = new File(['first_name,last_name'], 'staff.csv', { type: 'text/csv' });
    service.upload('STAFF', file).subscribe();

    const request = http.expectOne('/api/v1/imports?type=STAFF');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({});
  });

  it('submits the explicit mapping before validation', () => {
    service.map('job-1', { first_name: 'Prénom' }).subscribe();

    const request = http.expectOne('/api/v1/imports/job-1/mapping');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ mapping: { first_name: 'Prénom' } });
    request.flush({});
  });
});
