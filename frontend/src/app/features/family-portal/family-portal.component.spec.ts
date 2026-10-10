import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  FamilyDashboard,
  FamilyPortalApiService,
} from '../../core/family-portal/family-portal-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { PwaInstallService } from '../../core/pwa/pwa-install.service';
import { FamilyPortalComponent } from './family-portal.component';

describe('FamilyPortalComponent session routing', () => {
  const api = {
    dashboard: vi.fn(),
  };
  const admissions = {
    logout: vi.fn(() => of(undefined)),
  };
  const locale = {
    locale: signal<'fr-MA' | 'ar-MA'>('fr-MA'),
    setLocale: vi.fn(),
  };
  const pwa = {
    standalone: signal(false),
    activate: vi.fn(),
    install: vi.fn(),
  };

  beforeEach(async () => {
    api.dashboard.mockReset();
    admissions.logout.mockClear();
    await TestBed.configureTestingModule({
      imports: [FamilyPortalComponent],
      providers: [
        provideRouter([]),
        { provide: FamilyPortalApiService, useValue: api },
        { provide: AdmissionPortalApiService, useValue: admissions },
        { provide: LocaleService, useValue: locale },
        { provide: PwaInstallService, useValue: pwa },
      ],
    }).compileComponents();
  });

  it('redirects an unauthenticated family visit to the single login page', () => {
    api.dashboard.mockReturnValue(throwError(() => ({ status: 401 })));
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    TestBed.createComponent(FamilyPortalComponent);

    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { portal: 'family' },
      replaceUrl: true,
    });
  });

  it('deletes the family session before redirecting logout to the single login page', () => {
    api.dashboard.mockReturnValue(of({} as FamilyDashboard));
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(FamilyPortalComponent);

    (fixture.componentInstance as unknown as { logout(): void }).logout();

    expect(admissions.logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      replaceUrl: true,
    });
  });
});
