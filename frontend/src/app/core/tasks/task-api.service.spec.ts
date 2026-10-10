import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TaskApiService } from './task-api.service';

describe('TaskApiService', () => {
  let service: TaskApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TaskApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads filtered tasks with pagination', () => {
    service.dashboard('TODO', 'HIGH', 1, 10).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/tasks' && candidate.params.get('priority') === 'HIGH',
    );
    expect(request.request.params.get('status')).toBe('TODO');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({});
  });

  it('creates a task', () => {
    service
      .create({
        title: 'Appeler la famille',
        description: '',
        assigneeUserId: 'u-1',
        dueDate: '2026-09-02',
        priority: 'HIGH',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/tasks').request.method).toBe('POST');
  });

  it('moves and reassigns a task', () => {
    service.status('t-1', 'DONE').subscribe();
    expect(http.expectOne('/api/v1/tasks/t-1/status').request.body.status).toBe('DONE');
    service.assign('t-1', 'u-2').subscribe();
    expect(http.expectOne('/api/v1/tasks/t-1/assignee').request.body.assigneeUserId).toBe('u-2');
  });
});
