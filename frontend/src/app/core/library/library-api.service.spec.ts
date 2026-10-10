import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LibraryApiService } from './library-api.service';

describe('LibraryApiService', () => {
  let api: LibraryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LibraryApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(LibraryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the librarian cockpit', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/library/dashboard').flush({});
  });

  it('scans a barcode or QR code', () => {
    api.scan('SZ-LIB-QR-0001').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/library/copies/scan' &&
        candidate.params.get('code') === 'SZ-LIB-QR-0001',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('returns a loan without deleting its history', () => {
    api.returnLoan('loan-1', { returnedOn: '2026-08-10' }).subscribe();
    const request = http.expectOne('/api/v1/library/loans/loan-1/return');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('opens the independent student portal', () => {
    api.studentPortal().subscribe();
    http.expectOne('/api/v1/student-portal/library').flush({});
  });

  it('opens the family portal with its secure cookie', () => {
    api.familyPortal('student-1').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/public/library' &&
        candidate.params.get('studentId') === 'student-1',
    );
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });
});
