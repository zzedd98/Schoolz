import { HttpBackend, HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, firstValueFrom, Observable, of, shareReplay, tap } from 'rxjs';

import {
  AuthenticationResponse,
  CurrentUser,
  LoginCredentials,
  TokenRequestedResponse,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly accessTokenState = signal<string | null>(null);
  private readonly userState = signal<CurrentUser | null>(null);
  private refreshRequest?: Observable<AuthenticationResponse>;

  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.userState() !== null);

  async initialize(): Promise<void> {
    await firstValueFrom(this.refresh().pipe(catchError(() => of(null))));
  }

  login(credentials: LoginCredentials): Observable<AuthenticationResponse> {
    return this.http
      .post<AuthenticationResponse>('/api/v1/auth/login', credentials, {
        withCredentials: true,
      })
      .pipe(tap((response) => this.accept(response)));
  }

  refresh(): Observable<AuthenticationResponse> {
    if (!this.refreshRequest) {
      this.refreshRequest = this.http
        .post<AuthenticationResponse>(
          '/api/v1/auth/refresh',
          {},
          {
            withCredentials: true,
          },
        )
        .pipe(
          tap((response) => this.accept(response)),
          finalize(() => {
            this.refreshRequest = undefined;
          }),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.refreshRequest;
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(
        '/api/v1/auth/logout',
        {},
        {
          withCredentials: true,
        },
      )
      .pipe(finalize(() => this.clear()));
  }

  forgotPassword(tenantSlug: string, email: string): Observable<TokenRequestedResponse> {
    return this.http.post<TokenRequestedResponse>('/api/v1/auth/forgot-password', {
      tenantSlug,
      email,
    });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/reset-password', { token, newPassword });
  }

  activateAccount(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/activate-account', { token, newPassword });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/verify-email', { token });
  }

  accessToken(): string | null {
    return this.accessTokenState();
  }

  hasPermission(permission: string): boolean {
    return this.userState()?.permissions.includes(permission) ?? false;
  }

  clear(): void {
    this.accessTokenState.set(null);
    this.userState.set(null);
  }

  private accept(response: AuthenticationResponse): void {
    this.accessTokenState.set(response.accessToken);
    this.userState.set(response.user);
  }
}
