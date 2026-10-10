import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import {
  BusinessConfigurationApiService,
  BusinessConfigurationDashboard,
  BusinessConfigurationItem,
} from '../../core/business-configuration/business-configuration-api.service';

@Component({
  selector: 'app-business-configuration-center',
  imports: [DatePipe, FormsModule],
  templateUrl: './business-configuration-center.component.html',
  styleUrl: './business-configuration-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BusinessConfigurationCenterComponent {
  private readonly api = inject(BusinessConfigurationApiService);
  protected readonly auth = inject(AuthService);

  protected readonly data = signal<BusinessConfigurationDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busyId = signal<string | null>(null);
  protected readonly selectedCategory = signal('');
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly drafts = signal<Record<string, unknown>>({});
  protected readonly structuredDrafts = signal<Record<string, string>>({});
  protected readonly reasons = signal<Record<string, string>>({});

  protected readonly categories = [
    'ACADEMIC',
    'CALENDAR',
    'SCHEDULE',
    'FINANCE',
    'ATTENDANCE',
    'DISCIPLINE',
    'DOCUMENTS',
    'NOTIFICATIONS',
    'GRADING',
    'SECURITY',
  ];

  constructor() {
    this.load();
  }

  protected load(category = this.selectedCategory()): void {
    this.loading.set(true);
    this.error.set('');
    this.api.dashboard(category || undefined).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        const items = dashboard.categories.flatMap((section) => section.items);
        this.drafts.set(Object.fromEntries(items.map((item) => [item.id, item.value])));
        this.structuredDrafts.set(
          Object.fromEntries(
            items
              .filter((item) => item.valueType === 'JSON')
              .map((item) => [item.id, JSON.stringify(item.value, null, 2)]),
          ),
        );
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(
          response?.error?.detail ?? 'Le centre de configuration ne peut pas être chargé.',
        );
        this.loading.set(false);
      },
    });
  }

  protected selectCategory(category: string): void {
    const next = this.selectedCategory() === category ? '' : category;
    this.selectedCategory.set(next);
    this.load(next);
  }

  protected draft(id: string): unknown {
    return this.drafts()[id];
  }

  protected setDraft(id: string, value: unknown): void {
    this.drafts.update((current) => ({ ...current, [id]: value }));
  }

  protected setStructured(id: string, value: string): void {
    this.structuredDrafts.update((current) => ({ ...current, [id]: value }));
  }

  protected reason(id: string): string {
    return this.reasons()[id] ?? '';
  }

  protected setReason(id: string, value: string): void {
    this.reasons.update((current) => ({ ...current, [id]: value }));
  }

  protected hasOption(itemId: string, option: string): boolean {
    const value = this.draft(itemId);
    return Array.isArray(value) && value.includes(option);
  }

  protected toggleOption(itemId: string, option: string, checked: boolean): void {
    const current = this.draft(itemId);
    const selected = Array.isArray(current) ? current.map(String) : [];
    this.setDraft(
      itemId,
      checked
        ? [...selected.filter((item) => item !== option), option]
        : selected.filter((item) => item !== option),
    );
  }

  protected save(item: BusinessConfigurationItem): void {
    let value = this.draft(item.id);
    if (item.valueType === 'JSON') {
      try {
        value = JSON.parse(this.structuredDrafts()[item.id] ?? '');
      } catch {
        this.error.set(`${item.label} contient un JSON invalide.`);
        return;
      }
    }
    const reason = this.reason(item.id).trim();
    if (!reason) {
      this.error.set('Indiquez le motif de la modification avant d’enregistrer.');
      return;
    }
    this.busyId.set(item.id);
    this.error.set('');
    this.api.update(item.id, value, item.version, reason).subscribe({
      next: () => {
        this.success.set(`${item.label} a été versionné et appliqué.`);
        this.busyId.set(null);
        this.load();
      },
      error: (response) => {
        this.busyId.set(null);
        this.error.set(response?.error?.detail ?? 'La configuration n’a pas pu être enregistrée.');
      },
    });
  }

  protected reset(item: BusinessConfigurationItem): void {
    this.busyId.set(item.id);
    this.error.set('');
    this.api
      .reset(item.id, item.version, this.reason(item.id) || 'Retour à la valeur de référence')
      .subscribe({
        next: () => {
          this.success.set(`${item.label} utilise de nouveau sa valeur de référence.`);
          this.busyId.set(null);
          this.load();
        },
        error: (response) => {
          this.busyId.set(null);
          this.error.set(
            response?.error?.detail ?? 'La valeur de référence n’a pas pu être rétablie.',
          );
        },
      });
  }

  protected isCustomized(item: BusinessConfigurationItem): boolean {
    return JSON.stringify(item.value) !== JSON.stringify(item.defaultValue);
  }

  protected categoryName(value: string): string {
    return (
      {
        ACADEMIC: 'Périodes & coefficients',
        CALENDAR: 'Jours scolaires',
        SCHEDULE: 'Horaires',
        FINANCE: 'Frais & paiements',
        ATTENDANCE: 'Absences',
        DISCIPLINE: 'Vie éducative',
        DOCUMENTS: 'Documents',
        NOTIFICATIONS: 'Notifications',
        GRADING: 'Notes & calculs',
        SECURITY: 'Permissions',
      }[value] ?? value
    );
  }

  protected typeName(value: string): string {
    return (
      {
        TEXT: 'Texte',
        NUMBER: 'Nombre',
        BOOLEAN: 'Oui / non',
        TIME: 'Heure',
        SELECT: 'Choix unique',
        MULTI_SELECT: 'Choix multiples',
        JSON: 'Structure avancée',
        MONEY: 'Montant',
        PERCENT: 'Pourcentage',
      }[value] ?? value
    );
  }

  protected display(value: unknown): string {
    if (Array.isArray(value)) return value.join(' · ');
    if (value !== null && typeof value === 'object') return JSON.stringify(value);
    return String(value ?? '—');
  }
}
