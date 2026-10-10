import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BusinessConfigurationApiService } from './business-configuration-api.service';

describe('BusinessConfigurationApiService', () => {
  let service: BusinessConfigurationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BusinessConfigurationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the complete configuration dashboard', () => {
    service.dashboard().subscribe();
    expect(http.expectOne('/api/v1/business-configuration/dashboard').request.method).toBe('GET');
  });

  it('filters the dashboard by category', () => {
    service.dashboard('FINANCE').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/business-configuration/dashboard' &&
        candidate.params.get('category') === 'FINANCE',
    );
    expect(request.request.method).toBe('GET');
  });

  it('updates with a reason and expected version', () => {
    service.update('setting-1', 75, 3, 'Nouvel horaire').subscribe();
    const request = http.expectOne('/api/v1/business-configuration/items/setting-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      value: 75,
      expectedVersion: 3,
      reason: 'Nouvel horaire',
    });
  });

  it('resets without hiding the concurrency version', () => {
    service.reset('setting-1', 4, 'Retour au standard').subscribe();
    const request = http.expectOne('/api/v1/business-configuration/items/setting-1/reset');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.expectedVersion).toBe(4);
  });
});
