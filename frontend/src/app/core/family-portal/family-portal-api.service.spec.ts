import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FamilyPortalApiService } from './family-portal-api.service';

describe('FamilyPortalApiService', () => {
  let service: FamilyPortalApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FamilyPortalApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the selected child and date with the parent cookie', () => {
    service.dashboard('student-1', '2026-09-01').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/public/family-portal' &&
        candidate.params.get('studentId') === 'student-1' &&
        candidate.params.get('date') === '2026-09-01',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });

  it('persists a preferred child', () => {
    service.prefer('student-2').subscribe();
    const request = http.expectOne('/api/v1/public/family-portal/preference');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ studentId: 'student-2' });
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });
});
