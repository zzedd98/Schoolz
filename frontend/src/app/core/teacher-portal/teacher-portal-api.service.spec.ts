import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TeacherPortalApiService } from './teacher-portal-api.service';

describe('TeacherPortalApiService', () => {
  let service: TeacherPortalApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TeacherPortalApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a teacher day in an explicit date scope', () => {
    service.today('2026-09-01').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/teacher-portal/today' &&
        candidate.params.get('date') === '2026-09-01',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
