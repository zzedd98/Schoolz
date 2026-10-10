import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import {
  AssetAssignment,
  InventoryApiService,
  InventoryAsset,
  InventoryDashboard,
  InventoryItem,
} from '../../core/inventory/inventory-api.service';

type InventorySection = 'assets' | 'stock' | 'locations' | 'history';
type Drawer =
  'asset' | 'item' | 'movement' | 'location' | 'assignment' | 'return' | 'status' | null;

@Component({
  selector: 'app-inventory-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory-dashboard.component.html',
  styleUrl: './inventory-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryDashboardComponent {
  private readonly api = inject(InventoryApiService);

  protected readonly data = signal<InventoryDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeSection = signal<InventorySection>('assets');
  protected readonly drawer = signal<Drawer>(null);
  protected readonly selectedAsset = signal<InventoryAsset | null>(null);
  protected readonly selectedItem = signal<InventoryItem | null>(null);
  protected readonly selectedAssignment = signal<AssetAssignment | null>(null);

  protected assetForm = this.emptyAsset();
  protected itemForm = this.emptyItem();
  protected locationForm = {
    parentLocationId: '',
    code: '',
    name: '',
    locationType: 'ROOM',
    description: '',
  };
  protected movementForm = {
    movementType: 'TRANSFER',
    fromLocationId: '',
    toLocationId: '',
    quantity: 1,
    occurredOn: '2026-09-05',
    reference: '',
    reason: '',
  };
  protected assignmentForm = {
    destinationLocationId: '',
    assigneeType: 'PERSON',
    assigneeReference: '',
    assigneeLabel: '',
    assignedOn: '2026-09-05',
    dueOn: '2027-06-30',
    conditionOut: 'GOOD',
    note: '',
  };
  protected returnForm = {
    returnLocationId: '',
    returnedOn: '2026-09-05',
    conditionIn: 'GOOD',
    note: '',
  };
  protected statusForm = {
    status: 'MAINTENANCE',
    locationId: '',
    effectiveOn: '2026-09-05',
    condition: 'GOOD',
    note: '',
  };

  constructor() {
    this.load();
  }

  protected selectSection(section: InventorySection): void {
    this.activeSection.set(section);
  }

  protected openAsset(): void {
    this.assetForm = this.emptyAsset();
    this.open('asset');
  }

  protected openItem(): void {
    this.itemForm = this.emptyItem();
    this.open('item');
  }

  protected openLocation(): void {
    this.locationForm = {
      parentLocationId: '',
      code: '',
      name: '',
      locationType: 'ROOM',
      description: '',
    };
    this.open('location');
  }

  protected openMovement(item: InventoryItem): void {
    this.selectedItem.set(item);
    this.movementForm = {
      movementType: item.balances.length > 1 ? 'TRANSFER' : 'OUTBOUND',
      fromLocationId: item.balances[0]?.locationId ?? '',
      toLocationId: item.balances[1]?.locationId ?? '',
      quantity: 1,
      occurredOn: '2026-09-05',
      reference: '',
      reason: '',
    };
    this.open('movement');
  }

  protected openAssignment(asset: InventoryAsset): void {
    this.selectedAsset.set(asset);
    this.assignmentForm = {
      destinationLocationId: asset.locationId ?? '',
      assigneeType: 'PERSON',
      assigneeReference: '',
      assigneeLabel: '',
      assignedOn: '2026-09-05',
      dueOn: '2027-06-30',
      conditionOut: asset.condition,
      note: '',
    };
    this.open('assignment');
  }

  protected openReturn(asset: InventoryAsset): void {
    const assignment = this.activeAssignment(asset);
    if (!assignment) return;
    this.selectedAsset.set(asset);
    this.selectedAssignment.set(assignment);
    this.returnForm = {
      returnLocationId: asset.locationId ?? this.data()?.locations[0]?.id ?? '',
      returnedOn: '2026-09-05',
      conditionIn: asset.condition,
      note: '',
    };
    this.open('return');
  }

  protected openStatus(asset: InventoryAsset): void {
    this.selectedAsset.set(asset);
    this.statusForm = {
      status: asset.status === 'MAINTENANCE' ? 'AVAILABLE' : 'MAINTENANCE',
      locationId: asset.locationId ?? '',
      effectiveOn: '2026-09-05',
      condition: asset.condition,
      note: '',
    };
    this.open('status');
  }

  protected closeDrawer(): void {
    this.drawer.set(null);
    this.selectedAsset.set(null);
    this.selectedItem.set(null);
    this.selectedAssignment.set(null);
  }

  protected saveAsset(): void {
    this.mutate(
      this.api.createAsset({
        ...this.assetForm,
        acquisitionValue: this.assetForm.acquisitionValue || null,
        acquiredOn: this.assetForm.acquiredOn || null,
      }),
      'L’actif est identifié et son premier événement est historisé.',
      'L’actif est incomplet ou son identifiant existe déjà.',
    );
  }

  protected saveItem(): void {
    this.mutate(
      this.api.createItem({
        ...this.itemForm,
        openingLocationId: this.itemForm.openingLocationId || null,
        openingDate: this.itemForm.openingDate || null,
      }),
      'L’article et son stock initial sont enregistrés.',
      'L’article est incomplet ou son SKU existe déjà.',
    );
  }

  protected saveLocation(): void {
    this.mutate(
      this.api.createLocation({
        ...this.locationForm,
        campusId: null,
        parentLocationId: this.locationForm.parentLocationId || null,
      }),
      'Le nouvel emplacement est disponible.',
      'L’emplacement ne peut pas être créé.',
    );
  }

  protected saveMovement(): void {
    const item = this.selectedItem();
    if (!item) return;
    const type = this.movementForm.movementType;
    this.mutate(
      this.api.recordMovement(item.id, {
        ...this.movementForm,
        fromLocationId:
          type === 'INBOUND' || type === 'ADJUSTMENT_IN'
            ? null
            : this.movementForm.fromLocationId || null,
        toLocationId:
          type === 'OUTBOUND' || type === 'ADJUSTMENT_OUT'
            ? null
            : this.movementForm.toLocationId || null,
        reference: this.movementForm.reference || null,
      }),
      'Le mouvement est enregistré sans modifier l’historique précédent.',
      'Le mouvement est invalide ou le stock disponible est insuffisant.',
    );
  }

  protected saveAssignment(): void {
    const asset = this.selectedAsset();
    if (!asset) return;
    this.mutate(
      this.api.assignAsset(asset.id, {
        ...this.assignmentForm,
        destinationLocationId: this.assignmentForm.destinationLocationId || null,
        dueOn: this.assignmentForm.dueOn || null,
        assigneeReference: this.assignmentForm.assigneeReference || null,
      }),
      'L’actif est affecté et reste traçable jusqu’à sa restitution.',
      'L’actif n’est pas disponible ou l’affectation est incomplète.',
    );
  }

  protected saveReturn(): void {
    const assignment = this.selectedAssignment();
    if (!assignment) return;
    this.mutate(
      this.api.returnAsset(assignment.id, this.returnForm),
      'La restitution est contrôlée ; l’actif redevient disponible.',
      'La restitution ne peut pas être enregistrée.',
    );
  }

  protected saveStatus(): void {
    const asset = this.selectedAsset();
    if (!asset) return;
    this.mutate(
      this.api.changeAssetStatus(asset.id, {
        ...this.statusForm,
        locationId: this.statusForm.locationId || null,
      }),
      'Le statut est mis à jour et ajouté à l’historique.',
      'Le statut ne peut pas être modifié tant que l’actif est affecté.',
    );
  }

  protected activeAssignment(asset: InventoryAsset): AssetAssignment | undefined {
    return this.data()?.assignments.find(
      (assignment) => assignment.assetId === asset.id && assignment.status === 'ACTIVE',
    );
  }

  protected categoryIcon(category: string): string {
    const icons: Record<string, string> = {
      COMPUTER: '⌘',
      TABLET: '▣',
      PROJECTOR: '◈',
      BOOK: '▥',
      UNIFORM: '♙',
      TEACHING_MATERIAL: '✦',
      OTHER: '◇',
    };
    return icons[category] ?? '◇';
  }

  protected categoryLabel(category: string): string {
    const labels: Record<string, string> = {
      COMPUTER: 'Ordinateur',
      TABLET: 'Tablette',
      PROJECTOR: 'Projecteur',
      BOOK: 'Livre',
      UNIFORM: 'Uniforme',
      TEACHING_MATERIAL: 'Matériel pédagogique',
      OTHER: 'Autre',
    };
    return labels[category] ?? category;
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      AVAILABLE: 'Disponible',
      ASSIGNED: 'Affecté',
      MAINTENANCE: 'Maintenance',
      LOST: 'Perdu',
      RETIRED: 'Retiré',
      ACTIVE: 'En cours',
      RETURNED: 'Restitué',
    };
    return labels[status] ?? status;
  }

  protected movementLabel(type: string): string {
    const labels: Record<string, string> = {
      INBOUND: 'Entrée',
      OUTBOUND: 'Sortie',
      TRANSFER: 'Transfert',
      ADJUSTMENT_IN: 'Ajustement +',
      ADJUSTMENT_OUT: 'Ajustement −',
    };
    return labels[type] ?? type;
  }

  private open(kind: Exclude<Drawer, null>): void {
    this.drawer.set(kind);
    this.error.set('');
  }

  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Le carnet d’inventaire ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  private mutate(request: Observable<unknown>, success: string, failure: string): void {
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.notice.set(success);
        this.error.set('');
        this.saving.set(false);
        this.closeDrawer();
        this.load(false);
      },
      error: () => {
        this.error.set(failure);
        this.saving.set(false);
      },
    });
  }

  private emptyAsset() {
    return {
      locationId: '',
      assetTag: '',
      name: '',
      category: 'COMPUTER',
      brand: '',
      model: '',
      serialNumber: '',
      acquiredOn: '2026-09-05',
      acquisitionValue: 0,
      currency: 'MAD',
      condition: 'GOOD',
      notes: '',
    };
  }

  private emptyItem() {
    return {
      sku: '',
      name: '',
      category: 'TEACHING_MATERIAL',
      unit: 'unité',
      minimumStock: 0,
      description: '',
      openingLocationId: '',
      openingQuantity: 0,
      openingDate: '2026-09-05',
    };
  }
}
