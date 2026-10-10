import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ApiClientView {
  readonly id: string;
  readonly tenantId: string;
  readonly name: string;
  readonly description: string | null;
  readonly keyPrefix: string;
  readonly scopes: readonly string[];
  readonly rateLimitPerMinute: number;
  readonly active: boolean;
  readonly expiresAt: string | null;
  readonly lastUsedAt: string | null;
  readonly createdAt: string;
  readonly revokedAt: string | null;
}

export interface PublicApiDashboard {
  readonly generatedAt: string;
  readonly statistics: {
    readonly clientCount: number;
    readonly activeCount: number;
    readonly requestCount: number;
    readonly limitedCount: number;
  };
  readonly allowedScopes: readonly string[];
  readonly clients: readonly ApiClientView[];
  readonly requestLogs: readonly {
    readonly requestId: string;
    readonly clientName: string;
    readonly keyPrefix: string;
    readonly method: string;
    readonly path: string;
    readonly requiredScope: string;
    readonly statusCode: number;
    readonly outcome: string;
    readonly durationMs: number;
    readonly requestedAt: string;
  }[];
  readonly endpoints: readonly {
    readonly method: string;
    readonly path: string;
    readonly scope: string;
  }[];
}

export interface ApiKeyCommand {
  name: string;
  description: string;
  scopes: string[];
  rateLimitPerMinute: number;
  expiresAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class PublicApiAccessService {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<PublicApiDashboard> {
    return this.http.get<PublicApiDashboard>('/api/v1/api-access/dashboard');
  }

  create(command: ApiKeyCommand) {
    return this.http.post<{ readonly client: ApiClientView; readonly apiKey: string }>(
      '/api/v1/api-access/clients',
      command,
    );
  }

  revoke(clientId: string, reason: string) {
    return this.http.post<ApiClientView>(`/api/v1/api-access/clients/${clientId}/revoke`, {
      reason,
    });
  }
}
