import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

const PUBLIC_AUTH_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/forgot-password',
  '/api/v1/auth/reset-password',
  '/api/v1/auth/verify-email',
];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken();
  const authenticatedRequest = token
    ? request.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
        withCredentials: true,
      })
    : request.clone({ withCredentials: true });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      const canRefresh =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !PUBLIC_AUTH_PATHS.some((path) => request.url.includes(path));
      if (!canRefresh) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap(() => {
          const refreshedToken = auth.accessToken();
          return next(
            refreshedToken
              ? request.clone({
                  setHeaders: { Authorization: `Bearer ${refreshedToken}` },
                  withCredentials: true,
                })
              : request,
          );
        }),
        catchError((refreshError: unknown) => {
          auth.clear();
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
