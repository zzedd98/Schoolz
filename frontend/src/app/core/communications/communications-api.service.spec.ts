import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CommunicationsApiService } from './communications-api.service';

describe('CommunicationsApiService', () => {
  let service: CommunicationsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CommunicationsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the internal communication cockpit', () => {
    service.dashboard().subscribe();
    http.expectOne('/api/v1/communications/dashboard').flush({});
  });

  it('publishes an announcement with an explicit audience', () => {
    service
      .publishAnnouncement({
        title: 'Réunion',
        body: 'Rendez-vous à 17h.',
        audienceScope: 'CAMPUS',
        campusId: 'campus-1',
        classroomId: null,
      })
      .subscribe();
    const request = http.expectOne('/api/v1/communications/announcements');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.audienceScope).toBe('CAMPUS');
    request.flush({});
  });

  it('updates family notification preferences', () => {
    service
      .updatePreferences([{ category: 'DIRECT_MESSAGE', channel: 'WHATSAPP', enabled: false }])
      .subscribe();
    const request = http.expectOne('/api/v1/public/communications/preferences');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.preferences).toHaveLength(1);
    request.flush([]);
  });
});
