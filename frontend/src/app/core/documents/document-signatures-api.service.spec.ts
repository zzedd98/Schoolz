import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DocumentSignaturesApiService } from './document-signatures-api.service';

describe('DocumentSignaturesApiService', () => {
  let api: DocumentSignaturesApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DocumentSignaturesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a workflow pinned to the current document version', () => {
    api.create('document-1', 'parent-1').subscribe();
    const request = http.expectOne('/api/v1/document-signatures');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ documentId: 'document-1', portalAccountId: 'parent-1' });
    request.flush({ id: 'workflow-1', status: 'GENERATED' });
  });

  it('signs through the explicitly non-qualified local provider', () => {
    api.sign('workflow-1', 'Amina El Mansouri').subscribe();
    const request = http.expectOne('/api/v1/public/document-signatures/workflow-1/sign');
    expect(request.request.body).toEqual({
      signatoryName: 'Amina El Mansouri',
      providerKey: 'local-simple',
      confirmSimpleSignature: true,
    });
    request.flush({ status: 'SIGNED' });
  });
});
