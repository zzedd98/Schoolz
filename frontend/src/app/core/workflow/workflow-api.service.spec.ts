import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { WorkflowApiService } from './workflow-api.service';

describe('WorkflowApiService', () => {
  let api: WorkflowApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WorkflowApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the dashboard', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/workflows/dashboard').flush({});
  });

  it('creates a workflow', () => {
    const command = { code: 'TEST' } as never;
    api.create(command).subscribe();
    const request = http.expectOne('/api/v1/workflows');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBe(command);
    request.flush({});
  });

  it('changes activation', () => {
    api.activate('wf-1', false).subscribe();
    const request = http.expectOne('/api/v1/workflows/wf-1/activation');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ active: false });
    request.flush({});
  });

  it('dispatches an event', () => {
    const event = { eventId: 'evt-1' } as never;
    api.dispatch(event).subscribe();
    const request = http.expectOne('/api/v1/workflows/events');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBe(event);
    request.flush({});
  });
});
