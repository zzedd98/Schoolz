import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { environment } from '../../../environments/environment';

export const technicalFeatureGuard: CanActivateFn = () =>
  environment.showTechnicalFeatures || inject(Router).createUrlTree(['/app/dashboard']);
