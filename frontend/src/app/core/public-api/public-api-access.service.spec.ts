import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PublicApiAccessService } from './public-api-access.service';

describe('PublicApiAccessService', () => {
  let service: PublicApiAccessService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PublicApiAccessService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the API access dashboard', () => {
    service.dashboard().subscribe();
    expect(http.expectOne('/api/v1/api-access/dashboard').request.method).toBe('GET');
  });

  it('creates a scoped key', () => {
    service
      .create({
        name: 'SIS',
        description: 'Connector',
        scopes: ['students:read'],
        rateLimitPerMinute: 60,
        expiresAt: null,
      })
      .subscribe();
    const request = http.expectOne('/api/v1/api-access/clients');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.scopes).toEqual(['students:read']);
  });

  it('keeps the rate limit in the creation contract', () => {
    service
      .create({
        name: 'Compta',
        description: '',
        scopes: ['invoices:read'],
        rateLimitPerMinute: 25,
        expiresAt: null,
      })
      .subscribe();
    expect(http.expectOne('/api/v1/api-access/clients').request.body.rateLimitPerMinute).toBe(25);
  });

  it('revokes with an explicit reason', () => {
    service.revoke('client-1', 'Rotation annuelle').subscribe();
    const request = http.expectOne('/api/v1/api-access/clients/client-1/revoke');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.reason).toBe('Rotation annuelle');
  });
});
