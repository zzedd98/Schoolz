import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface BusinessConfigurationItem {
  readonly id: string;
  readonly category: string;
  readonly key: string;
  readonly label: string;
  readonly description: string;
  readonly valueType: string;
  readonly value: unknown;
  readonly defaultValue: unknown;
  readonly options: readonly string[];
  readonly constraints: Readonly<Record<string, unknown>>;
  readonly sourceModule: string;
  readonly editable: boolean;
  readonly version: number;
  readonly updatedAt: string;
}

export interface BusinessConfigurationDashboard {
  readonly generatedAt: string;
  readonly selectedCategory: string | null;
  readonly statistics: {
    readonly itemCount: number;
    readonly categoryCount: number;
    readonly customizedCount: number;
    readonly guardedCount: number;
  };
  readonly categories: readonly {
    readonly key: string;
    readonly items: readonly BusinessConfigurationItem[];
  }[];
  readonly recentRevisions: readonly {
    readonly id: string;
    readonly itemId: string;
    readonly key: string;
    readonly label: string;
    readonly version: number;
    readonly previousValue: unknown;
    readonly nextValue: unknown;
    readonly reason: string;
    readonly changedAt: string;
  }[];
}

@Injectable({ providedIn: 'root' })
export class BusinessConfigurationApiService {
  private readonly http = inject(HttpClient);

  dashboard(category?: string): Observable<BusinessConfigurationDashboard> {
    return this.http.get<BusinessConfigurationDashboard>(
      '/api/v1/business-configuration/dashboard',
      category ? { params: { category } } : {},
    );
  }

  update(itemId: string, value: unknown, expectedVersion: number, reason: string) {
    return this.http.put<BusinessConfigurationItem>(
      `/api/v1/business-configuration/items/${itemId}`,
      { value, expectedVersion, reason },
    );
  }

  reset(itemId: string, expectedVersion: number, reason: string) {
    return this.http.post<BusinessConfigurationItem>(
      `/api/v1/business-configuration/items/${itemId}/reset`,
      { expectedVersion, reason },
    );
  }
}
