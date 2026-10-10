import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface InventoryLocation {
  readonly id: string;
  readonly campusId: string | null;
  readonly campusName: string | null;
  readonly parentLocationId: string | null;
  readonly parentLocationName: string | null;
  readonly code: string;
  readonly name: string;
  readonly locationType: string;
  readonly description: string | null;
  readonly active: boolean;
  readonly assetCount: number;
  readonly stockedItemCount: number;
}

export interface StockBalance {
  readonly id: string;
  readonly itemId: string;
  readonly sku: string;
  readonly itemName: string;
  readonly locationId: string;
  readonly locationCode: string;
  readonly locationName: string;
  readonly quantity: number;
  readonly updatedAt: string;
}

export interface InventoryItem {
  readonly id: string;
  readonly sku: string;
  readonly name: string;
  readonly category: string;
  readonly unit: string;
  readonly minimumStock: number;
  readonly description: string | null;
  readonly active: boolean;
  readonly totalQuantity: number;
  readonly lowStock: boolean;
  readonly balances: readonly StockBalance[];
}

export interface StockMovement {
  readonly id: string;
  readonly itemId: string;
  readonly sku: string;
  readonly itemName: string;
  readonly fromLocationId: string | null;
  readonly fromLocationName: string | null;
  readonly toLocationId: string | null;
  readonly toLocationName: string | null;
  readonly movementType: string;
  readonly quantity: number;
  readonly occurredOn: string;
  readonly reference: string | null;
  readonly reason: string;
  readonly recordedById: string;
  readonly recordedByName: string;
  readonly recordedAt: string;
}

export interface InventoryAsset {
  readonly id: string;
  readonly locationId: string | null;
  readonly locationCode: string | null;
  readonly locationName: string | null;
  readonly assetTag: string;
  readonly name: string;
  readonly category: string;
  readonly brand: string | null;
  readonly model: string | null;
  readonly serialNumber: string | null;
  readonly acquiredOn: string | null;
  readonly acquisitionValue: number | null;
  readonly currency: string;
  readonly condition: string;
  readonly status: string;
  readonly notes: string | null;
  readonly activeAssignee: string | null;
  readonly historyCount: number;
}

export interface AssetAssignment {
  readonly id: string;
  readonly assetId: string;
  readonly assetTag: string;
  readonly assetName: string;
  readonly destinationLocationId: string | null;
  readonly destinationLocationName: string | null;
  readonly assigneeType: string;
  readonly assigneeReference: string | null;
  readonly assigneeLabel: string;
  readonly assignedOn: string;
  readonly dueOn: string | null;
  readonly returnedOn: string | null;
  readonly conditionOut: string;
  readonly conditionIn: string | null;
  readonly note: string | null;
  readonly assignedByName: string;
  readonly returnedByName: string | null;
  readonly status: string;
}

export interface AssetEvent {
  readonly id: string;
  readonly assetId: string;
  readonly assetTag: string;
  readonly assetName: string;
  readonly assignmentId: string | null;
  readonly eventType: string;
  readonly eventDate: string;
  readonly fromStatus: string | null;
  readonly toStatus: string;
  readonly locationId: string | null;
  readonly locationName: string | null;
  readonly detail: string;
  readonly recordedByName: string;
  readonly recordedAt: string;
}

export interface InventoryDashboard {
  readonly generatedAt: string;
  readonly statistics: {
    readonly assets: number;
    readonly assignedAssets: number;
    readonly maintenanceAssets: number;
    readonly inventoryItems: number;
    readonly lowStockItems: number;
    readonly movements: number;
  };
  readonly locations: readonly InventoryLocation[];
  readonly items: readonly InventoryItem[];
  readonly balances: readonly StockBalance[];
  readonly movements: readonly StockMovement[];
  readonly assets: readonly InventoryAsset[];
  readonly assignments: readonly AssetAssignment[];
  readonly assetHistory: readonly AssetEvent[];
}

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<InventoryDashboard>('/api/v1/inventory/dashboard');
  }

  createLocation(command: object) {
    return this.http.post<InventoryLocation>('/api/v1/inventory/locations', command);
  }

  createItem(command: object) {
    return this.http.post<InventoryItem>('/api/v1/inventory/items', command);
  }

  recordMovement(itemId: string, command: object) {
    return this.http.post<StockMovement>(`/api/v1/inventory/items/${itemId}/movements`, command);
  }

  createAsset(command: object) {
    return this.http.post<InventoryAsset>('/api/v1/inventory/assets', command);
  }

  assignAsset(assetId: string, command: object) {
    return this.http.post<AssetAssignment>(
      `/api/v1/inventory/assets/${assetId}/assignments`,
      command,
    );
  }

  returnAsset(assignmentId: string, command: object) {
    return this.http.post<AssetAssignment>(
      `/api/v1/inventory/assignments/${assignmentId}/return`,
      command,
    );
  }

  changeAssetStatus(assetId: string, command: object) {
    return this.http.put<InventoryAsset>(`/api/v1/inventory/assets/${assetId}/status`, command);
  }
}
