import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const auth = {
    login: vi.fn(() => throwError(() => new Error('not an internal account'))),
  };
  const admissions = {
    login: vi.fn(() =>
      of({
        tenantId: 'tenant-id',
        accountId: 'parent-id',
        email: 'parent.demo@schoolz.ma',
        displayName: 'Famille Démo',
        locale: 'fr-MA',
      }),
    ),
  };
  const locale = {
    locale: signal<'fr-MA' | 'ar-MA'>('fr-MA'),
    translate: (key: string) => key,
    setLocale: vi.fn(),
  };
  const tenantPreference = {
    read: vi.fn(() => ''),
    remember: vi.fn(),
  };

  beforeEach(async () => {
    auth.login.mockClear();
    admissions.login.mockClear();
    tenantPreference.remember.mockClear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AdmissionPortalApiService, useValue: admissions },
        { provide: LocaleService, useValue: locale },
        { provide: TenantPreferenceService, useValue: tenantPreference },
      ],
    }).compileComponents();
  });

  it('falls back to the family session and redirects the parent to the family portal', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance as unknown as {
      form: { setValue(value: { tenantSlug: string; email: string; password: string }): void };
    };
    component.form.setValue({
      tenantSlug: 'ecole-demo',
      email: 'parent.demo@schoolz.ma',
      password: 'SchoolZParent2026!',
    });
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')
      ?.dispatchEvent(new Event('submit'));

    expect(auth.login).toHaveBeenCalledWith({
      tenantSlug: 'ecole-demo',
      email: 'parent.demo@schoolz.ma',
      password: 'SchoolZParent2026!',
    });
    expect(admissions.login).toHaveBeenCalledWith({
      tenantSlug: 'ecole-demo',
      email: 'parent.demo@schoolz.ma',
      password: 'SchoolZParent2026!',
    });
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).toHaveBeenCalledWith('/family');
    expect(tenantPreference.remember).toHaveBeenCalledWith('ecole-demo');
  });
});
