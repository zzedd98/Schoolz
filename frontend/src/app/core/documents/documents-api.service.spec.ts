import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DocumentsApiService } from './documents-api.service';

describe('DocumentsApiService', () => {
  let api: DocumentsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DocumentsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the internal DMS dashboard', () => {
    api.dashboard().subscribe();
    const request = http.expectOne('/api/v1/documents/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush({ categories: [], documents: [], parents: [] });
  });

  it('requests a temporary family download URL', () => {
    api.familySignedUrl('document-1').subscribe();
    const request = http.expectOne('/api/v1/public/documents/document-1/signed-url');
    expect(request.request.method).toBe('POST');
    request.flush({ url: '/download', maxDownloads: 3 });
  });

  it('uploads document metadata and bytes as multipart form data', () => {
    const file = new File(['pdf'], 'piece.pdf', { type: 'application/pdf' });
    api
      .upload(
        {
          categoryId: 'category-1',
          documentType: 'PARENT_ID',
          title: "Pièce d'identité",
          description: '',
          ownerKind: 'SCHOOL',
          ownerReferenceId: null,
          visibility: 'INTERNAL',
          expiresOn: null,
        },
        file,
      )
      .subscribe();
    const request = http.expectOne('/api/v1/documents');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    request.flush({ id: 'document-1' });
  });
});
