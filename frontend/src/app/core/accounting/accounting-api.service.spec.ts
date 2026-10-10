import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AccountingApiService } from './accounting-api.service';

describe('AccountingApiService', () => {
  let api: AccountingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AccountingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the accounting cockpit', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/accounting/dashboard').flush({});
  });

  it('posts a supplier invoice through its business workflow', () => {
    api.postSupplierInvoice('invoice-1').subscribe();
    const request = http.expectOne('/api/v1/accounting/supplier-invoices/invoice-1/post');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('synchronizes the family ledger without accepting manual lines', () => {
    api.syncFamilyLedger().subscribe();
    const request = http.expectOne('/api/v1/accounting/family-ledger/sync');
    expect(request.request.body).toEqual({});
    request.flush({ postedEntries: 0, totalFamilyLedgerEntries: 2 });
  });
});
