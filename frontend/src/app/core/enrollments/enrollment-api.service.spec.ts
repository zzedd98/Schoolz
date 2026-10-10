import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EnrollmentApiService } from './enrollment-api.service';

describe('EnrollmentApiService', () => {
  let service: EnrollmentApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EnrollmentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the parent workflows with the admission cookie', () => {
    service.portalWorkflows().subscribe((workflows) => expect(workflows).toEqual([]));
    const request = http.expectOne('/api/v1/public/enrollments');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush([]);
  });

  it('sends selected services and payment plan', () => {
    service
      .confirmServices('workflow-1', {
        transportSelected: true,
        transportRoute: 'Casa Centre',
        cafeteriaSelected: true,
        activities: ['ROBOTICS'],
        paymentPlan: 'MONTHLY',
      })
      .subscribe();
    const request = http.expectOne('/api/v1/public/enrollments/workflow-1/services');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.paymentPlan).toBe('MONTHLY');
    expect(request.request.body.activities).toEqual(['ROBOTICS']);
    request.flush({});
  });

  it('assigns a class through the internal workflow endpoint', () => {
    service.assignClass('workflow-1', 'class-1').subscribe();
    const request = http.expectOne('/api/v1/enrollments/workflow-1/class');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ classroomId: 'class-1' });
    request.flush({});
  });
});
