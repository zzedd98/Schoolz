import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StudentApiService } from './student-api.service';

describe('StudentApiService', () => {
  let service: StudentApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StudentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches students through the tenant-scoped endpoint', () => {
    service.students('Alaoui').subscribe();

    const request = http.expectOne('/api/v1/students/page?page=0&size=40&search=Alaoui');
    expect(request.request.method).toBe('GET');
    request.flush({ page: 0, size: 40, hasNext: false, items: [] });
  });

  it('loads one complete 360 record', () => {
    service.student('student-1').subscribe();

    const request = http.expectOne('/api/v1/students/student-1');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
