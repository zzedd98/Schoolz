import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthenticationResponse } from './auth.models';
import { AuthService } from './auth.service';

const response: AuthenticationResponse = {
  accessToken: 'signed-access-token',
  accessExpiresAt: '2026-08-08T00:10:00Z',
  user: {
    id: '9023e89b-783e-4213-b6c3-65f00c4394af',
    tenantId: '0eddbfd8-76f7-4849-b842-3d96dfa25544',
    email: 'admin@schoolz.ma',
    displayName: 'Administrateur SchoolZ',
    locale: 'fr-MA',
    roles: ['SUPER_ADMIN'],
    permissions: ['user.read'],
  },
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the access token in memory after login', () => {
    service
      .login({
        tenantSlug: 'ecole-demo',
        email: 'admin@schoolz.ma',
        password: 'SchoolZDemo2026!',
      })
      .subscribe();

    http.expectOne('/api/v1/auth/login').flush(response);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('signed-access-token');
    expect(service.user()?.tenantId).toBe(response.user.tenantId);
  });

  it('clears the in-memory session after logout', () => {
    service
      .login({
        tenantSlug: 'ecole-demo',
        email: 'admin@schoolz.ma',
        password: 'SchoolZDemo2026!',
      })
      .subscribe();
    http.expectOne('/api/v1/auth/login').flush(response);

    service.logout().subscribe();
    http.expectOne('/api/v1/auth/logout').flush(null);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
  });
});
