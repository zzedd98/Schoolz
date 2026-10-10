import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BillingApiService, FamilyFinanceApiService } from './billing-api.service';

describe('BillingApiService', () => {
  let http: HttpTestingController;
  let billing: BillingApiService;
  let family: FamilyFinanceApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    billing = TestBed.inject(BillingApiService);
    family = TestBed.inject(FamilyFinanceApiService);
  });

  afterEach(() => http.verify());

  it('loads the tenant finance dashboard', () => {
    billing.dashboard().subscribe();
    http.expectOne('/api/v1/billing/dashboard').flush({ accounts: [] });
  });

  it('records one payment with multiple allocations', () => {
    billing
      .recordPayment('family-1', {
        amount: 1200,
        paymentMethod: 'BANK_TRANSFER',
        allocations: [
          { invoiceId: 'invoice-1', amount: 700 },
          { invoiceId: 'invoice-2', amount: 500 },
        ],
      })
      .subscribe();
    const request = http.expectOne('/api/v1/billing/accounts/family-1/payments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.allocations).toHaveLength(2);
    request.flush({});
  });

  it('loads the family ledger with its opaque cookie session', () => {
    family.ledger().subscribe();
    const request = http.expectOne('/api/v1/public/finance');
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });

  it('loads collections with aging and triggers an idempotent automatic run', () => {
    billing.collections('2026-08-09').subscribe();
    http.expectOne('/api/v1/billing/collections/dashboard?asOf=2026-08-09').flush({ plans: [] });

    billing.runAutomaticReminders('2026-08-09').subscribe();
    const request = http.expectOne('/api/v1/billing/collections/reminders/run?asOf=2026-08-09');
    expect(request.request.method).toBe('POST');
    request.flush({ createdActions: 0, skippedActions: 0 });
  });

  it('loads family installment plans with the opaque cookie session', () => {
    family.installmentPlans().subscribe();
    const request = http.expectOne('/api/v1/public/finance/installment-plans');
    expect(request.request.withCredentials).toBe(true);
    request.flush([]);
  });

  it('initializes an idempotent family sandbox payment', () => {
    family
      .initializeOnlinePayment(
        {
          providerKey: 'sandbox',
          amount: 1500,
          invoiceId: 'invoice-1',
          returnUrl: '/finance',
        },
        'browser-key-0001',
      )
      .subscribe();
    const request = http.expectOne('/api/v1/public/finance/online-payments');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('Idempotency-Key')).toBe('browser-key-0001');
    request.flush({ status: 'PENDING' });
  });

  it('loads and verifies tenant online payments', () => {
    billing.onlinePayments('family-1').subscribe();
    http.expectOne('/api/v1/billing/online-payments?accountId=family-1').flush([]);

    billing.verifyOnlinePayment('online-1').subscribe();
    const request = http.expectOne('/api/v1/billing/online-payments/online-1/verify');
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'PAID' });
  });
});
