import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-password-recovery',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule, RouterLink],
  templateUrl: './password-recovery.component.html',
  styleUrl: './password-recovery.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordRecoveryComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tenantPreference = inject(TenantPreferenceService);

  protected readonly activationMode = this.route.snapshot.routeConfig?.path === 'activate-account';
  protected readonly resetMode =
    this.route.snapshot.routeConfig?.path === 'reset-password' || this.activationMode;
  protected readonly pending = signal(false);
  protected readonly completed = signal(false);
  protected readonly failed = signal(false);
  protected readonly developmentToken = signal<string | null>(null);

  protected readonly forgotForm = new FormGroup({
    tenantSlug: new FormControl(this.tenantPreference.read(), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });
  protected readonly resetForm = new FormGroup({
    token: new FormControl(this.route.snapshot.queryParamMap.get('token') ?? '', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected requestReset(): void {
    if (this.forgotForm.invalid || this.pending()) {
      this.forgotForm.markAllAsTouched();
      return;
    }
    const request = this.forgotForm.getRawValue();
    this.begin();
    this.auth
      .forgotPassword(request.tenantSlug, request.email)
      .pipe(
        finalize(() => this.pending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (response) => {
          this.developmentToken.set(response.developmentToken);
          this.completed.set(true);
        },
        error: () => this.failed.set(true),
      });
  }

  protected resetPassword(): void {
    if (this.resetForm.invalid || this.pending()) {
      this.resetForm.markAllAsTouched();
      return;
    }
    const request = this.resetForm.getRawValue();
    if (request.newPassword !== request.confirmPassword) {
      this.failed.set(true);
      return;
    }
    this.begin();
    const operation = this.activationMode
      ? this.auth.activateAccount(request.token, request.newPassword)
      : this.auth.resetPassword(request.token, request.newPassword);
    operation
      .pipe(
        finalize(() => this.pending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          if (this.activationMode) {
            this.tenantPreference.remember(this.route.snapshot.queryParamMap.get('tenant') ?? '');
          }
          this.completed.set(true);
        },
        error: () => this.failed.set(true),
      });
  }

  private begin(): void {
    this.pending.set(true);
    this.failed.set(false);
    this.completed.set(false);
  }
}
