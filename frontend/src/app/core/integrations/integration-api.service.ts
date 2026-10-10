import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface IntegrationStatistics {
  readonly adapterCount: number;
  readonly readyAdapters: number;
  readonly transferCount: number;
  readonly importedRows: number;
  readonly exportedRows: number;
  readonly errorRows: number;
}

export interface IntegrationSchema {
  readonly dataset: string;
  readonly label: string;
  readonly headers: readonly string[];
  readonly importable: boolean;
  readonly exportable: boolean;
}

export interface MappingProfile {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly dataset: string;
  readonly direction: string;
  readonly format: string;
  readonly fieldMapping: Readonly<Record<string, string>>;
  readonly active: boolean;
}

export interface IntegrationAdapter {
  readonly id: string;
  readonly key: string;
  readonly name: string;
  readonly category: string;
  readonly status: string;
  readonly transportMode: string;
  readonly capabilities: readonly string[];
  readonly disclaimer: string;
  readonly schemas: readonly IntegrationSchema[];
  readonly profiles: readonly MappingProfile[];
}

export interface IntegrationTransfer {
  readonly id: string;
  readonly adapterKey: string;
  readonly adapterName: string;
  readonly direction: string;
  readonly dataset: string;
  readonly format: string;
  readonly status: string;
  readonly fileName: string;
  readonly rowCount: number;
  readonly successCount: number;
  readonly errorCount: number;
  readonly sha256: string;
  readonly fileAvailable: boolean;
  readonly createdAt: string;
  readonly completedAt: string;
}

export interface IntegrationDashboard {
  readonly generatedAt: string;
  readonly statistics: IntegrationStatistics;
  readonly adapters: readonly IntegrationAdapter[];
  readonly jobs: readonly IntegrationTransfer[];
  readonly compliance: {
    readonly massarScraping: boolean;
    readonly officialMassarApi: boolean;
    readonly statement: string;
  };
}

@Injectable({ providedIn: 'root' })
export class IntegrationApiService {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<IntegrationDashboard> {
    return this.http.get<IntegrationDashboard>('/api/v1/integrations/dashboard');
  }

  export(adapterKey: string, dataset: string, format: string): Observable<IntegrationTransfer> {
    return this.http.post<IntegrationTransfer>(`/api/v1/integrations/${adapterKey}/exports`, {
      dataset,
      format,
    });
  }

  import(adapterKey: string, dataset: string, file: File): Observable<IntegrationTransfer> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<IntegrationTransfer>(`/api/v1/integrations/${adapterKey}/imports`, form, {
      params: { dataset },
    });
  }

  download(jobId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`/api/v1/integrations/jobs/${jobId}/file`, {
      observe: 'response',
      responseType: 'blob',
    });
  }
}
