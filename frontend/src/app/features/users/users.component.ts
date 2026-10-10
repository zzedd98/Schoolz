import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin } from 'rxjs';

import {
  AdminApiService,
  PortalProfileOption,
  RoleView,
  UserAccountView,
} from '../../core/admin/admin-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { LocaleService } from '../../core/i18n/locale.service';

type Panel = 'user' | 'role' | null;

@Component({
  selector: 'app-users',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersComponent {
  protected readonly i18n = inject(LocaleService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(AdminApiService);
  private readonly tenantPreference = inject(TenantPreferenceService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly users = signal<readonly UserAccountView[]>([]);
  protected readonly roles = signal<readonly RoleView[]>([]);
  protected readonly permissions = signal<readonly string[]>([]);
  protected readonly staffProfiles = signal<readonly PortalProfileOption[]>([]);
  protected readonly studentProfiles = signal<readonly PortalProfileOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly saving = signal(false);
  protected readonly activeTab = signal<'users' | 'roles'>('users');
  protected readonly panel = signal<Panel>(null);
  protected readonly activationLink = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  protected readonly userForm = new FormGroup({
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    locale: new FormControl<'fr-MA' | 'ar-MA'>('fr-MA', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    roleCodes: new FormControl<string[]>([], {
      nonNullable: true,
      validators: [Validators.required],
    }),
    staffProfileId: new FormControl<string | null>(null),
    studentId: new FormControl<string | null>(null),
  });
  protected readonly roleForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    permissions: new FormControl<string[]>([], {
      nonNullable: true,
    }),
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      users: this.api.users(),
      roles: this.api.roles(),
      permissions: this.api.permissions(),
      portalProfiles: this.api.portalProfileOptions(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ users, roles, permissions, portalProfiles }) => {
          this.users.set(users);
          this.roles.set(roles);
          this.permissions.set([...permissions].sort());
          this.staffProfiles.set(
            portalProfiles.staff.filter((profile) => !profile.linkedAccountId),
          );
          this.studentProfiles.set(
            portalProfiles.students.filter((profile) => !profile.linkedAccountId),
          );
        },
        error: () => this.failed.set(true),
      });
  }

  protected open(panel: Exclude<Panel, null>): void {
    this.activationLink.set(null);
    this.success.set(null);
    this.failed.set(false);
    this.panel.set(panel);
  }

  protected close(): void {
    this.panel.set(null);
    this.activationLink.set(null);
  }

  protected createUser(): void {
    if (this.userForm.invalid || this.userForm.controls.roleCodes.value.length === 0) {
      this.userForm.markAllAsTouched();
      return;
    }
    const roles = this.userForm.controls.roleCodes.value;
    if (
      (roles.includes('TEACHER') && !this.userForm.controls.staffProfileId.value) ||
      (roles.includes('STUDENT') && !this.userForm.controls.studentId.value)
    ) {
      this.userForm.markAllAsTouched();
      this.failed.set(true);
      return;
    }
    this.saving.set(true);
    this.failed.set(false);
    this.api
      .createUser(this.userForm.getRawValue())
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (created) => {
          this.users.update((users) => [...users, created.user]);
          const tenant = encodeURIComponent(this.tenantPreference.read());
          this.activationLink.set(
            `${globalThis.location.origin}${created.activationPath}&tenant=${tenant}`,
          );
          this.success.set(this.i18n.translate('users.created'));
          this.userForm.reset({
            displayName: '',
            email: '',
            locale: 'fr-MA',
            roleCodes: [],
            staffProfileId: null,
            studentId: null,
          });
        },
        error: () => this.failed.set(true),
      });
  }

  protected createRole(): void {
    if (this.roleForm.invalid) {
      this.roleForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.failed.set(false);
    this.api
      .createRole(this.roleForm.getRawValue())
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (role) => {
          this.roles.update((roles) => [...roles, role]);
          this.success.set(this.i18n.translate('roles.created'));
          this.roleForm.reset({ code: '', name: '', permissions: [] });
        },
        error: () => this.failed.set(true),
      });
  }

  protected initials(name: string): string {
    return name
      .split(' ')
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }

  protected hasSelectedRole(code: string): boolean {
    return this.userForm.controls.roleCodes.value.includes(code);
  }

  protected async copyActivationLink(): Promise<void> {
    const link = this.activationLink();
    if (!link || !globalThis.navigator?.clipboard) return;
    await globalThis.navigator.clipboard.writeText(link);
  }
}
