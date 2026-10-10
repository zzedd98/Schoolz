import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface SystemStatusResponse {
  readonly application: string;
  readonly status: 'UP';
  readonly timestamp: string;
  readonly defaultLocale: 'fr-MA';
  readonly supportedLocales: readonly string[];
  readonly currency: string;
  readonly timezone: string;
}

@Injectable({ providedIn: 'root' })
export class SystemApiService {
  private readonly http = inject(HttpClient);

  status(): Observable<SystemStatusResponse> {
    return this.http.get<SystemStatusResponse>('/api/v1/system/status');
  }
}
