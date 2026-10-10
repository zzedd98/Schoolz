import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ProcurementApiService } from './procurement-api.service';

describe('ProcurementApiService', () => {
  let api: ProcurementApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProcurementApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ProcurementApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the purchase cockpit', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/procurement/dashboard').flush({});
  });

  it('submits a request through its business transition', () => {
    api.submitRequest('request-1').subscribe();
    const request = http.expectOne('/api/v1/procurement/requests/request-1/submit');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('records supplier payment through a matched invoice', () => {
    api.payInvoice('match-1', { amount: 1200 }).subscribe();
    const request = http.expectOne('/api/v1/procurement/invoice-matches/match-1/payments');
    expect(request.request.body).toEqual({ amount: 1200 });
    request.flush({});
  });
});
