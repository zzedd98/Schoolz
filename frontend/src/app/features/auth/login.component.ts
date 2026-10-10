import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, finalize, map } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { environment } from '../../../environments/environment';
import { DemoAccount } from '../../../environments/environment.model';

@Component({
  selector: 'app-login',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private static readonly FAMILY_RETURN_PATHS = [
    '/family',
    '/library',
    '/finance',
    '/messages',
    '/documents',
    '/signatures',
    '/transport',
    '/cafeteria',
    '/activities',
    '/enrollment',
    '/childcare',
    '/attendance',
    '/homework',
  ] as const;
  protected readonly i18n = inject(LocaleService);
  private readonly auth = inject(AuthService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tenantPreference = inject(TenantPreferenceService);

  protected readonly submitting = signal(false);
  protected readonly failed = signal(false);
  protected readonly demoAccounts = environment.demoAccounts;
  protected readonly rememberedTenant = this.tenantPreference.read();
  protected readonly familyPortalRequested =
    this.route.snapshot.queryParamMap.get('portal') === 'family';
  protected readonly form = new FormGroup({
    tenantSlug: new FormControl(this.rememberedTenant, {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected useDemo(account: DemoAccount): void {
    this.form.setValue({
      tenantSlug: this.demoAccounts?.tenantSlug ?? '',
      email: account.email,
      password: account.password,
    });
    this.failed.set(false);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.failed.set(false);
    this.submitting.set(true);
    const credentials = this.form.getRawValue();
    this.auth
      .login(credentials)
      .pipe(
        map((response) => ({ kind: 'staff' as const, response })),
        catchError(() =>
          this.admissions
            .login(credentials)
            .pipe(map(() => ({ kind: 'family' as const, response: null }))),
        ),
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.tenantPreference.remember(credentials.tenantSlug);
          if (result.kind === 'family') {
            void this.router.navigateByUrl(this.familyReturnUrl());
            return;
          }
          const target = result.response.user.roles.includes('STUDENT')
            ? '/student'
            : result.response.user.roles.includes('TEACHER')
              ? '/app/teacher'
              : '/app/dashboard';
          void this.router.navigate([target]);
        },
        error: () => this.failed.set(true),
      });
  }

  private familyReturnUrl(): string {
    const requested = this.route.snapshot.queryParamMap.get('returnUrl') ?? '';
    return LoginComponent.FAMILY_RETURN_PATHS.some(
      (path) => requested === path || requested.startsWith(`${path}?`),
    )
      ? requested
      : '/family';
  }
}
