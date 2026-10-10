import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type GlobalSearchType =
  'STUDENT' | 'PARENT' | 'TEACHER' | 'INVOICE' | 'CLASSROOM' | 'DOCUMENT';

export interface GlobalSearchItem {
  readonly type: GlobalSearchType;
  readonly sourceId: string;
  readonly displayName: string;
  readonly secondaryText: string;
  readonly routePath: string;
  readonly metadata: Readonly<Record<string, unknown>>;
  readonly indexedAt: string;
}

export interface GlobalSearchResponse {
  readonly query: string;
  readonly normalizedQuery: string;
  readonly page: number;
  readonly size: number;
  readonly hasNext: boolean;
  readonly tookMs: number;
  readonly availableTypes: readonly GlobalSearchType[];
  readonly facets: Readonly<Partial<Record<GlobalSearchType, number>>>;
  readonly items: readonly GlobalSearchItem[];
}

@Injectable({ providedIn: 'root' })
export class GlobalSearchApiService {
  private readonly http = inject(HttpClient);

  search(query: string, type = '', page = 0, size = 20) {
    let params = new HttpParams().set('q', query).set('page', page).set('size', size);
    if (type) params = params.set('type', type);
    return this.http.get<GlobalSearchResponse>('/api/v1/search', { params });
  }

  refresh() {
    return this.http.post<{
      readonly indexedAt: string;
      readonly documentCount: number;
      readonly counts: Readonly<Partial<Record<GlobalSearchType, number>>>;
    }>('/api/v1/search/refresh', {});
  }
}
