import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { InventoryApiService } from './inventory-api.service';

describe('InventoryApiService', () => {
  let api: InventoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [InventoryApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InventoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the inventory cockpit', () => {
    api.dashboard().subscribe();
    http.expectOne('/api/v1/inventory/dashboard').flush({});
  });

  it('records a stock movement on its item', () => {
    api.recordMovement('item-1', { quantity: 4 }).subscribe();
    const request = http.expectOne('/api/v1/inventory/items/item-1/movements');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 4 });
    request.flush({});
  });

  it('assigns an identified asset', () => {
    api.assignAsset('asset-1', { assigneeLabel: 'Salle 2' }).subscribe();
    const request = http.expectOne('/api/v1/inventory/assets/asset-1/assignments');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('returns an assignment without deleting its history', () => {
    api.returnAsset('assignment-1', { conditionIn: 'GOOD' }).subscribe();
    const request = http.expectOne('/api/v1/inventory/assignments/assignment-1/return');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });
});
