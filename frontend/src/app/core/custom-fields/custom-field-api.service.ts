import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CustomFieldDefinition {
  readonly id: string;
  readonly entityType: string;
  readonly key: string;
  readonly label: string;
  readonly description: string | null;
  readonly type: string;
  readonly options: readonly string[];
  readonly required: boolean;
  readonly readPermission: string | null;
  readonly writePermission: string | null;
  readonly displayOrder: number;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CustomFieldValue {
  readonly id: string | null;
  readonly definitionId: string;
  readonly entityId: string;
  readonly key: string;
  readonly value: unknown;
  readonly updatedAt: string | null;
}

export interface CustomFieldDashboard {
  readonly generatedAt: string;
  readonly entityType: string;
  readonly selectedEntityId: string | null;
  readonly statistics: {
    readonly definitionCount: number;
    readonly securedCount: number;
    readonly populatedCount: number;
    readonly emptyCount: number;
  };
  readonly entities: readonly { readonly id: string; readonly label: string }[];
  readonly definitions: readonly CustomFieldDefinition[];
  readonly values: readonly CustomFieldValue[];
  readonly supportedEntityTypes: readonly string[];
  readonly supportedFieldTypes: readonly string[];
  readonly availablePermissions: readonly string[];
}

export interface CustomFieldDefinitionCommand {
  entityType: string;
  key: string;
  label: string;
  description: string;
  type: string;
  options: string[];
  required: boolean;
  readPermission: string | null;
  writePermission: string | null;
  displayOrder: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class CustomFieldApiService {
  private readonly http = inject(HttpClient);

  dashboard(entityType: string, entityId?: string | null): Observable<CustomFieldDashboard> {
    const params: Record<string, string> = { entityType };
    if (entityId) params['entityId'] = entityId;
    return this.http.get<CustomFieldDashboard>('/api/v1/custom-fields/dashboard', { params });
  }

  create(command: CustomFieldDefinitionCommand): Observable<CustomFieldDefinition> {
    return this.http.post<CustomFieldDefinition>('/api/v1/custom-fields/definitions', command);
  }

  update(
    definitionId: string,
    command: CustomFieldDefinitionCommand,
  ): Observable<CustomFieldDefinition> {
    return this.http.put<CustomFieldDefinition>(
      `/api/v1/custom-fields/definitions/${definitionId}`,
      command,
    );
  }

  putValue(definitionId: string, entityId: string, value: unknown): Observable<CustomFieldValue> {
    return this.http.put<CustomFieldValue>(
      `/api/v1/custom-fields/definitions/${definitionId}/entities/${entityId}`,
      { value },
    );
  }

  clearValue(definitionId: string, entityId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/v1/custom-fields/definitions/${definitionId}/entities/${entityId}`,
    );
  }
}
