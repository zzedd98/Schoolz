import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CampusView, OrganizationApiService } from './organization-api.service';

const campus: CampusView = {
  id: 'campus-a',
  code: 'CASA',
  name: 'Campus Casablanca',
  addressLine1: '1 avenue Atlas',
  addressLine2: null,
  city: 'Casablanca',
  region: 'Casablanca-Settat',
  postalCode: '20000',
  countryCode: 'MA',
  phone: null,
  email: null,
  locale: 'fr-MA',
  timezone: 'Africa/Casablanca',
  active: true,
  offeredGradeLevelIds: [],
};

describe('OrganizationApiService', () => {
  let service: OrganizationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrganizationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads all campuses through the tenant-scoped endpoint', () => {
    let result: readonly CampusView[] = [];
    service.campuses().subscribe((campuses) => (result = campuses));

    const request = http.expectOne('/api/v1/campuses');
    expect(request.request.method).toBe('GET');
    request.flush([campus]);

    expect(result).toHaveLength(1);
    expect(result[0].code).toBe('CASA');
  });

  it('replaces the offered levels of a campus explicitly', () => {
    service.setCampusOfferings('campus-a', ['grade-1', 'grade-2']).subscribe();

    const request = http.expectOne('/api/v1/campuses/campus-a/grade-levels');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ gradeLevelIds: ['grade-1', 'grade-2'] });
    request.flush({ ...campus, offeredGradeLevelIds: ['grade-1', 'grade-2'] });
  });
});
