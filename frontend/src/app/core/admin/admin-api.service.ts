import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'DISABLED';

export interface UserAccountView {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly locale: 'fr-MA' | 'ar-MA';
  readonly status: AccountStatus;
  readonly emailVerifiedAt: string | null;
  readonly lockedUntil: string | null;
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface RoleView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly systemRole: boolean;
  readonly permissions: readonly string[];
}

export interface CreateUserRequest {
  readonly email: string;
  readonly displayName: string;
  readonly locale: 'fr-MA' | 'ar-MA';
  readonly roleCodes: readonly string[];
  readonly staffProfileId: string | null;
  readonly studentId: string | null;
}

export interface CreatedUserResponse {
  readonly user: UserAccountView;
  readonly activationPath: string;
}

export interface PortalProfileOption {
  readonly id: string;
  readonly displayName: string;
  readonly reference: string | null;
  readonly linkedAccountId: string | null;
}

export interface PortalProfileOptions {
  readonly staff: readonly PortalProfileOption[];
  readonly students: readonly PortalProfileOption[];
}

export interface CreateRoleRequest {
  readonly code: string;
  readonly name: string;
  readonly permissions: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  users() {
    return this.http.get<readonly UserAccountView[]>('/api/v1/admin/users');
  }

  roles() {
    return this.http.get<readonly RoleView[]>('/api/v1/admin/roles');
  }

  permissions() {
    return this.http.get<readonly string[]>('/api/v1/admin/permissions');
  }

  portalProfileOptions() {
    return this.http.get<PortalProfileOptions>('/api/v1/admin/users/portal-profile-options');
  }

  createUser(request: CreateUserRequest) {
    return this.http.post<CreatedUserResponse>('/api/v1/admin/users', request);
  }

  createRole(request: CreateRoleRequest) {
    return this.http.post<RoleView>('/api/v1/admin/roles', request);
  }
}
