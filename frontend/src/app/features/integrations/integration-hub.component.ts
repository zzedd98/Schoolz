import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  IntegrationAdapter,
  IntegrationApiService,
  IntegrationDashboard,
  IntegrationSchema,
  IntegrationTransfer,
} from '../../core/integrations/integration-api.service';

@Component({
  selector: 'app-integration-hub',
  imports: [FormsModule],
  templateUrl: './integration-hub.component.html',
  styleUrl: './integration-hub.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IntegrationHubComponent {
  private readonly api = inject(IntegrationApiService);

  protected readonly data = signal<IntegrationDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly drawer = signal<'export' | 'import' | null>(null);
  protected readonly selectedAdapterKey = signal('massar-file');
  protected readonly selectedDataset = signal('STUDENTS');
  protected readonly selectedFormat = signal('CSV');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly selectedAdapter = computed(
    () =>
      this.data()?.adapters.find((adapter) => adapter.key === this.selectedAdapterKey()) ?? null,
  );
  protected readonly selectedSchema = computed(
    () =>
      this.selectedAdapter()?.schemas.find((schema) => schema.dataset === this.selectedDataset()) ??
      null,
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Le centre d’intégrations ne peut pas être chargé pour le moment.');
        this.loading.set(false);
      },
    });
  }

  protected open(adapter: IntegrationAdapter, mode: 'export' | 'import'): void {
    const schema = adapter.schemas.find((item) =>
      mode === 'export' ? item.exportable : item.importable,
    );
    if (!schema) return;
    this.selectedAdapterKey.set(adapter.key);
    this.selectedDataset.set(schema.dataset);
    this.selectedFormat.set('CSV');
    this.selectedFile.set(null);
    this.error.set('');
    this.drawer.set(mode);
  }

  protected compatibleSchemas(mode: 'export' | 'import'): readonly IntegrationSchema[] {
    return (
      this.selectedAdapter()?.schemas.filter((schema) =>
        mode === 'export' ? schema.exportable : schema.importable,
      ) ?? []
    );
  }

  protected supports(adapter: IntegrationAdapter, mode: 'export' | 'import'): boolean {
    return adapter.schemas.some((schema) =>
      mode === 'export' ? schema.exportable : schema.importable,
    );
  }

  protected createExport(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .export(this.selectedAdapterKey(), this.selectedDataset(), this.selectedFormat())
      .subscribe({
        next: (job) => {
          this.busy.set(false);
          this.drawer.set(null);
          this.success.set(`${job.fileName} est prêt et son empreinte a été enregistrée.`);
          this.load();
          this.download(job);
        },
        error: (response) => {
          this.busy.set(false);
          this.error.set(response?.error?.detail ?? 'L’export n’a pas pu être généré.');
        },
      });
  }

  protected chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
  }

  protected validateImport(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.busy.set(true);
    this.error.set('');
    this.api.import(this.selectedAdapterKey(), this.selectedDataset(), file).subscribe({
      next: (job) => {
        this.busy.set(false);
        this.drawer.set(null);
        this.success.set(
          `${job.successCount} ligne(s) validée(s), ${job.errorCount} anomalie(s). Aucune donnée métier n’a été écrite.`,
        );
        this.load();
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(response?.error?.detail ?? 'Le fichier n’a pas pu être validé.');
      },
    });
  }

  protected download(job: IntegrationTransfer): void {
    this.api.download(job.id).subscribe({
      next: (response) => {
        if (!response.body) return;
        const url = URL.createObjectURL(response.body);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = job.fileName;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('Le fichier d’export n’est plus disponible.'),
    });
  }

  protected category(value: string): string {
    return (
      {
        EDUCATION: 'Scolarité',
        SPREADSHEET: 'Bureautique',
        ACCOUNTING: 'Comptabilité',
        PAYMENT: 'Paiement',
        MESSAGING: 'Messagerie',
      }[value] ?? value
    );
  }

  protected status(value: string): string {
    return (
      {
        COMPLETED: 'Export terminé',
        VALIDATED: 'Import validé',
        COMPLETED_WITH_ERRORS: 'À corriger',
        FAILED: 'Échec',
      }[value] ?? value
    );
  }

  protected dateTime(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', { dateStyle: 'medium', timeStyle: 'short' }).format(
      new Date(value),
    );
  }

  protected shortHash(value: string): string {
    return value ? `${value.slice(0, 8)}…${value.slice(-6)}` : '—';
  }
}
