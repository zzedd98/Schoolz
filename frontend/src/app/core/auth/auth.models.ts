export interface CurrentUser {
  readonly id: string;
  readonly tenantId: string;
  readonly email: string;
  readonly displayName: string;
  readonly locale: 'fr-MA' | 'ar-MA';
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
}

export interface AuthenticationResponse {
  readonly accessToken: string;
  readonly accessExpiresAt: string;
  readonly user: CurrentUser;
}

export interface LoginCredentials {
  readonly tenantSlug: string;
  readonly email: string;
  readonly password: string;
}

export interface TokenRequestedResponse {
  readonly message: string;
  readonly developmentToken: string | null;
}
