import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  CustomFieldApiService,
  CustomFieldDashboard,
  CustomFieldDefinition,
  CustomFieldDefinitionCommand,
} from '../../core/custom-fields/custom-field-api.service';

@Component({
  selector: 'app-custom-field-studio',
  imports: [FormsModule],
  templateUrl: './custom-field-studio.component.html',
  styleUrl: './custom-field-studio.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomFieldStudioComponent {
  private readonly api = inject(CustomFieldApiService);

  protected readonly data = signal<CustomFieldDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly builderOpen = signal(false);
  protected readonly entityType = signal('STUDENT');
  protected readonly entityId = signal<string | null>(null);
  protected readonly fieldValues = signal<Record<string, unknown>>({});
  protected readonly savingId = signal<string | null>(null);
  protected optionsText = '';
  protected form: CustomFieldDefinitionCommand = this.emptyDefinition();

  constructor() {
    this.load();
  }

  protected load(entityId: string | null = this.entityId()): void {
    this.loading.set(true);
    this.api.dashboard(this.entityType(), entityId).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.entityId.set(dashboard.selectedEntityId);
        this.fieldValues.set(
          Object.fromEntries(dashboard.values.map((item) => [item.definitionId, item.value])),
        );
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(response?.error?.detail ?? 'L’atelier sur mesure ne peut pas être chargé.');
        this.loading.set(false);
      },
    });
  }

  protected selectType(type: string): void {
    this.entityType.set(type);
    this.entityId.set(null);
    this.load(null);
  }

  protected selectEntity(id: string): void {
    this.entityId.set(id);
    this.load(id);
  }

  protected value(definitionId: string): unknown {
    return this.fieldValues()[definitionId];
  }

  protected setValue(definitionId: string, value: unknown): void {
    this.fieldValues.update((current) => ({ ...current, [definitionId]: value }));
  }

  protected hasOption(definitionId: string, option: string): boolean {
    const current = this.value(definitionId);
    return Array.isArray(current) && current.includes(option);
  }

  protected toggleOption(definitionId: string, option: string, checked: boolean): void {
    const current = this.value(definitionId);
    const selected = Array.isArray(current) ? current.map(String) : [];
    this.setValue(
      definitionId,
      checked
        ? [...selected.filter((item) => item !== option), option]
        : selected.filter((item) => item !== option),
    );
  }

  protected save(definition: CustomFieldDefinition): void {
    const entityId = this.entityId();
    if (!entityId) return;
    this.savingId.set(definition.id);
    this.error.set('');
    this.api.putValue(definition.id, entityId, this.value(definition.id)).subscribe({
      next: () => {
        this.savingId.set(null);
        this.success.set(`${definition.label} a été enregistré.`);
        this.load(entityId);
      },
      error: (response) => {
        this.savingId.set(null);
        this.error.set(response?.error?.detail ?? 'La valeur n’a pas pu être enregistrée.');
      },
    });
  }

  protected clear(definition: CustomFieldDefinition): void {
    const entityId = this.entityId();
    if (!entityId || definition.required) return;
    this.api.clearValue(definition.id, entityId).subscribe({
      next: () => {
        this.success.set(`${definition.label} a été effacé.`);
        this.load(entityId);
      },
      error: (response) =>
        this.error.set(response?.error?.detail ?? 'La valeur n’a pas pu être effacée.'),
    });
  }

  protected openBuilder(): void {
    this.form = this.emptyDefinition();
    this.form.entityType = this.entityType();
    this.optionsText = '';
    this.builderOpen.set(true);
  }

  protected create(): void {
    this.busy.set(true);
    this.error.set('');
    this.form.options = this.usesOptions(this.form.type)
      ? this.optionsText
          .split('\n')
          .map((item) => item.trim())
          .filter(Boolean)
      : [];
    this.api.create(this.form).subscribe({
      next: () => {
        this.busy.set(false);
        this.builderOpen.set(false);
        this.success.set('Le champ est disponible immédiatement sur ce type de dossier.');
        this.load();
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(response?.error?.detail ?? 'La définition est incomplète ou invalide.');
      },
    });
  }

  protected usesOptions(type: string): boolean {
    return type === 'SELECT' || type === 'MULTI_SELECT';
  }

  protected typeName(value: string): string {
    return (
      {
        TEXT: 'Texte',
        NUMBER: 'Nombre',
        DATE: 'Date',
        BOOLEAN: 'Oui / non',
        SELECT: 'Choix unique',
        MULTI_SELECT: 'Choix multiples',
      }[value] ?? value
    );
  }

  protected entityName(value: string): string {
    return (
      {
        STUDENT: 'Élèves',
        PERSON: 'Personnes',
        HOUSEHOLD: 'Familles',
        EMPLOYEE: 'Collaborateurs',
        ADMISSION: 'Candidatures',
        INVOICE: 'Factures',
      }[value] ?? value
    );
  }

  private emptyDefinition(): CustomFieldDefinitionCommand {
    return {
      entityType: this.entityType(),
      key: '',
      label: '',
      description: '',
      type: 'TEXT',
      options: [],
      required: false,
      readPermission: null,
      writePermission: null,
      displayOrder: 50,
      active: true,
    };
  }
}
