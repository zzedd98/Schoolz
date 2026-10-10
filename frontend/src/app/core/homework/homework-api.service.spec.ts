import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HomeworkApiService } from './homework-api.service';

describe('HomeworkApiService', () => {
  let service: HomeworkApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HomeworkApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the teacher dashboard in an explicit class, term and date scope', () => {
    service.dashboard('class-1', 'term-1', '2026-09-01', '2026-09-30').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/homework/dashboard' &&
        candidate.params.get('classroomId') === 'class-1' &&
        candidate.params.get('academicTermId') === 'term-1' &&
        candidate.params.get('from') === '2026-09-01' &&
        candidate.params.get('to') === '2026-09-30',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('uploads a real multipart attachment before publication', () => {
    const file = new File(['fiche'], 'fiche.txt', { type: 'text/plain' });
    service.uploadAttachment('lesson-contents', 'lesson-1', file).subscribe();
    const request = http.expectOne('/api/v1/homework/lesson-contents/lesson-1/attachments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    const uploaded = (request.request.body as FormData).get('file') as File;
    expect(uploaded.name).toBe('fiche.txt');
    expect(uploaded.type).toBe('text/plain');
    request.flush({});
  });

  it('keeps parent calendar and status calls credentialed', () => {
    service.parentDashboard('2026-09-01', '2026-09-30', 'student-1').subscribe();
    const dashboard = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/public/homework' &&
        candidate.params.get('studentId') === 'student-1',
    );
    expect(dashboard.request.withCredentials).toBe(true);
    dashboard.flush({});

    service.markStatus('homework-1', 'student-1', 'DONE').subscribe();
    const status = http.expectOne('/api/v1/public/homework/assignments/homework-1/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.withCredentials).toBe(true);
    expect(status.request.body).toEqual({ studentId: 'student-1', status: 'DONE' });
    status.flush({});
  });
});
