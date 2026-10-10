import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { WebhookApiService } from './webhook-api.service';

describe('WebhookApiService', () => {
  let service: WebhookApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WebhookApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the dashboard', () => {
    service.dashboard().subscribe();
    http.expectOne('/api/v1/webhooks/dashboard').flush({});
  });

  it('creates a subscription', () => {
    service
      .create({
        name: 'SIS',
        endpointUrl: 'https://example.test/hooks',
        eventTypes: ['student.created'],
        maxAttempts: 5,
        timeoutSeconds: 10,
      })
      .subscribe();
    const request = http.expectOne('/api/v1/webhooks/subscriptions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.eventTypes).toEqual(['student.created']);
    request.flush({});
  });

  it('publishes an idempotent event', () => {
    service.publish('invoice.paid', 'invoice-42', { amount: 1200 }).subscribe();
    const request = http.expectOne('/api/v1/webhooks/events');
    expect(request.request.body.idempotencyKey).toBe('invoice-42');
    request.flush({});
  });

  it('processes due deliveries', () => {
    service.processDue().subscribe();
    expect(http.expectOne('/api/v1/webhooks/deliveries/process-due').request.method).toBe('POST');
  });

  it('retries a delivery with a reason', () => {
    service.retry('delivery-1', 'Récepteur corrigé').subscribe();
    const request = http.expectOne('/api/v1/webhooks/deliveries/delivery-1/retry');
    expect(request.request.body.reason).toBe('Récepteur corrigé');
    request.flush({});
  });
});
