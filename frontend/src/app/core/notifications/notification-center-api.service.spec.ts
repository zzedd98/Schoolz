import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationCenterApiService } from './notification-center-api.service';

describe('NotificationCenterApiService', () => {
  let service: NotificationCenterApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificationCenterApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads one filtered page', () => {
    service.center('UNREAD', 2, 10).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/notification-center' &&
        candidate.params.get('status') === 'UNREAD' &&
        candidate.params.get('page') === '2',
    );
    expect(request.request.params.get('size')).toBe('10');
    request.flush({});
  });

  it('marks a notification as read and archives it', () => {
    service.read('n-1').subscribe();
    expect(http.expectOne('/api/v1/notification-center/n-1/read').request.method).toBe('PATCH');
    service.archive('n-1').subscribe();
    expect(http.expectOne('/api/v1/notification-center/n-1/archive').request.method).toBe('PATCH');
  });

  it('updates push email and sms preferences together', () => {
    service.preferences({ PUSH: true, EMAIL: true, SMS: false }).subscribe();
    const request = http.expectOne('/api/v1/notification-center/preferences');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.channels.SMS).toBe(false);
    request.flush([]);
  });
});
