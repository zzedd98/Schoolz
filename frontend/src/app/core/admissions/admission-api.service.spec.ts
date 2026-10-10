import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdmissionApiService, BoardCard } from './admission-api.service';

describe('AdmissionApiService', () => {
  let service: AdmissionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdmissionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a campaign board with its tenant JWT interceptor path', () => {
    service.board('campaign-1').subscribe((board) => expect(board.campaignId).toBe('campaign-1'));
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/admissions/board' &&
        candidate.params.get('campaignId') === 'campaign-1',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ campaignId: 'campaign-1', columns: [] });
  });

  it('moves a card through the Kanban endpoint', () => {
    const card = {
      id: 'application-1',
      cardType: 'APPLICATION',
      status: 'REVIEW',
    } as BoardCard;
    service.move(card, 'ACCEPTED').subscribe();
    const request = http.expectOne('/api/v1/admissions/pipeline');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body.cardType).toBe('APPLICATION');
    expect(request.request.body.cardId).toBe('application-1');
    expect(request.request.body.targetStatus).toBe('ACCEPTED');
    request.flush(null);
  });
});
