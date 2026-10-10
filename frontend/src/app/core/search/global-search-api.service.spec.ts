import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GlobalSearchApiService } from './global-search-api.service';

describe('GlobalSearchApiService', () => {
  let service: GlobalSearchApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GlobalSearchApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches all authorized resources with bounded pagination', () => {
    service.search('Yásmine', '', 1, 10).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/search' &&
        candidate.params.get('q') === 'Yásmine' &&
        candidate.params.get('page') === '1' &&
        candidate.params.get('size') === '10',
    );
    expect(request.request.params.has('type')).toBe(false);
    request.flush({});
  });

  it('passes the selected resource type', () => {
    service.search('فاتورة', 'INVOICE').subscribe();
    const request = http.expectOne((candidate) => candidate.params.get('type') === 'INVOICE');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('refreshes the PostgreSQL index', () => {
    service.refresh().subscribe();
    expect(http.expectOne('/api/v1/search/refresh').request.method).toBe('POST');
  });
});
