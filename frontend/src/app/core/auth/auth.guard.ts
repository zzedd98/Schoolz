import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AdmissionPortalApiService } from '../admissions/admission-portal-api.service';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? true : inject(Router).createUrlTree(['/login']);
};

export const workspaceGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  return auth.user()?.roles.includes('STUDENT') ? router.createUrlTree(['/student']) : true;
};

export const studentGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  return auth.user()?.roles.includes('STUDENT') ? true : router.createUrlTree(['/app/dashboard']);
};

export const familySessionGuard: CanActivateFn = (_route, state) => {
  const admissions = inject(AdmissionPortalApiService);
  const router = inject(Router);
  return admissions.home().pipe(
    map(() => true),
    catchError((response: HttpErrorResponse) =>
      of(
        response.status === 401
          ? router.createUrlTree(['/login'], {
              queryParams: { portal: 'family', returnUrl: state.url },
            })
          : true,
      ),
    ),
  );
};
