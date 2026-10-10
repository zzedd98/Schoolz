import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CustomFieldApiService } from './custom-field-api.service';

describe('CustomFieldApiService', () => {
  let api: CustomFieldApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CustomFieldApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads one typed entity workspace', () => {
    api.dashboard('STUDENT', 'student-1').subscribe();
    http
      .expectOne(
        (request) =>
          request.url === '/api/v1/custom-fields/dashboard' &&
          request.params.get('entityType') === 'STUDENT' &&
          request.params.get('entityId') === 'student-1',
      )
      .flush({});
  });

  it('creates a definition', () => {
    const command = { key: 'bus_code' } as never;
    api.create(command).subscribe();
    const request = http.expectOne('/api/v1/custom-fields/definitions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBe(command);
    request.flush({});
  });

  it('updates a definition', () => {
    api.update('field-1', { label: 'Code bus' } as never).subscribe();
    expect(http.expectOne('/api/v1/custom-fields/definitions/field-1').request.method).toBe('PUT');
  });

  it('writes a typed value', () => {
    api.putValue('field-1', 'student-1', ['Robotique']).subscribe();
    const request = http.expectOne('/api/v1/custom-fields/definitions/field-1/entities/student-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ value: ['Robotique'] });
    request.flush({});
  });

  it('clears an optional value', () => {
    api.clearValue('field-1', 'student-1').subscribe();
    expect(
      http.expectOne('/api/v1/custom-fields/definitions/field-1/entities/student-1').request.method,
    ).toBe('DELETE');
  });
});
