import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdmissionPortalApiService } from './admission-portal-api.service';

describe('AdmissionPortalApiService', () => {
  let service: AdmissionPortalApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdmissionPortalApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the HttpOnly portal cookie for the family home', () => {
    service.home().subscribe((home) => expect(home.account.email).toBe('parent@example.ma'));
    const request = http.expectOne('/api/v1/public/admissions/me');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      account: { email: 'parent@example.ma' },
      applicants: [],
      applications: [],
      notifications: [],
    });
  });

  it('uploads a document without forcing a multipart content type', () => {
    const file = new File(['document'], 'birth.pdf', { type: 'application/pdf' });
    service.uploadDocument('application-1', 'BIRTH_CERTIFICATE', file).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/public/admissions/applications/application-1/documents' &&
        candidate.params.get('documentType') === 'BIRTH_CERTIFICATE',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toBeInstanceOf(FormData);
    expect(request.request.headers.has('Content-Type')).toBe(false);
    request.flush({ id: 'document-1' });
  });
});
