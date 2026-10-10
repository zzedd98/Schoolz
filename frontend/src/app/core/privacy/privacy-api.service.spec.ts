import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PrivacyApiService, PrivacyRequest } from './privacy-api.service';

describe('PrivacyApiService', () => {
  let service: PrivacyApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PrivacyApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the privacy register', () => {
    service.dashboard().subscribe();
    expect(http.expectOne('/api/v1/privacy').request.method).toBe('GET');
  });

  it('creates and advances a rights request', () => {
    service
      .createRequest({
        requestType: 'ACCESS',
        subjectType: 'STUDENT',
        subjectReference: 'SZ-001',
        notes: '',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/privacy/requests').request.method).toBe('POST');
    service.updateRequest('r-1', 'IN_REVIEW').subscribe();
    expect(http.expectOne('/api/v1/privacy/requests/r-1/status').request.body.status).toBe(
      'IN_REVIEW',
    );
  });

  it('tracks exports, retention and hosting', () => {
    service.prepareExport({ id: 'r-1' } as PrivacyRequest).subscribe();
    expect(http.expectOne('/api/v1/privacy/exports').request.method).toBe('POST');
    service
      .configureRetention({
        dataCategory: 'Logs',
        retentionDays: 90,
        expiryAction: 'DELETE',
        legalHold: false,
      })
      .subscribe();
    expect(http.expectOne('/api/v1/privacy/retention').request.method).toBe('PUT');
    service
      .configureHosting({
        countryCode: 'MA',
        regionLabel: 'Casablanca',
        providerName: 'Provider',
        encryptionAtRest: true,
        encryptionInTransit: true,
        crossBorderTransfer: false,
        legalValidationStatus: 'PENDING',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/privacy/hosting').request.method).toBe('PUT');
  });
});
