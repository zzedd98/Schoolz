import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type ImportType =
  'STUDENTS' | 'PARENTS' | 'STAFF' | 'CLASSES' | 'PAYMENTS' | 'OPENING_BALANCES';

export type ImportStatus = 'UPLOADED' | 'MAPPED' | 'VALIDATED' | 'IMPORTED' | 'FAILED';

export interface ImportField {
  readonly key: string;
  readonly label: string;
  readonly required: boolean;
  readonly example: string;
  readonly aliases: readonly string[];
}

export interface ImportJob {
  readonly id: string;
  readonly type: ImportType;
  readonly typeLabel: string;
  readonly fileName: string;
  readonly fileFormat: 'CSV' | 'XLSX';
  readonly status: ImportStatus;
  readonly headers: readonly string[];
  readonly fields: readonly ImportField[];
  readonly mapping: Readonly<Record<string, string>>;
  readonly totalRows: number;
  readonly validRows: number;
  readonly invalidRows: number;
  readonly importedRows: number;
  readonly errorSummary: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface ValidationIssue {
  readonly field: string;
  readonly message: string;
}

export interface ImportRow {
  readonly rowNumber: number;
  readonly values: Readonly<Record<string, string>>;
  readonly errors: readonly ValidationIssue[];
  readonly status: 'PENDING' | 'VALID' | 'INVALID' | 'IMPORTED';
  readonly targetRecordId: string | null;
}

export interface ImportPreview {
  readonly job: ImportJob;
  readonly rows: readonly ImportRow[];
  readonly truncated: boolean;
}

@Injectable({ providedIn: 'root' })
export class DataImportApiService {
  private readonly http = inject(HttpClient);

  jobs() {
    return this.http.get<readonly ImportJob[]>('/api/v1/imports');
  }

  upload(type: ImportType, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<ImportJob>('/api/v1/imports', body, { params: { type } });
  }

  map(jobId: string, mapping: Readonly<Record<string, string>>) {
    return this.http.put<ImportJob>(`/api/v1/imports/${jobId}/mapping`, { mapping });
  }

  validate(jobId: string) {
    return this.http.post<ImportPreview>(`/api/v1/imports/${jobId}/validate`, {});
  }

  preview(jobId: string) {
    return this.http.get<ImportPreview>(`/api/v1/imports/${jobId}/preview`);
  }

  execute(jobId: string) {
    return this.http.post<ImportJob>(`/api/v1/imports/${jobId}/execute`, {});
  }

  template(type: ImportType) {
    return this.http.get(`/api/v1/imports/templates/${type}.xlsx`, { responseType: 'blob' });
  }
}
