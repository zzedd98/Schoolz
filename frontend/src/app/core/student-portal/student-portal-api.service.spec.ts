import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StudentPortalApiService } from './student-portal-api.service';

describe('StudentPortalApiService', () => {
  let service: StudentPortalApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StudentPortalApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the compact student dashboard', () => {
    service.dashboard('2026-09-01').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/student-portal/dashboard' &&
        candidate.params.get('date') === '2026-09-01',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('loads every item for dedicated student pages', () => {
    service.all().subscribe();
    const request = http.expectOne('/api/v1/student-portal/all');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
