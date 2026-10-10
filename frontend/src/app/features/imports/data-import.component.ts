import { KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { finalize, Observable } from 'rxjs';

import {
  DataImportApiService,
  ImportJob,
  ImportPreview,
  ImportType,
} from '../../core/imports/data-import-api.service';
import { LocaleService, TranslationKey } from '../../core/i18n/locale.service';

interface TypeChoice {
  readonly type: ImportType;
  readonly label: TranslationKey;
  readonly icon: string;
}

@Component({
  selector: 'app-data-import',
  imports: [KeyValuePipe, MatButtonModule],
  templateUrl: './data-import.component.html',
  styleUrl: './data-import.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataImportComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(DataImportApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly types: readonly TypeChoice[] = [
    { type: 'STUDENTS', label: 'imports.students', icon: 'É' },
    { type: 'PARENTS', label: 'imports.parents', icon: 'P' },
    { type: 'STAFF', label: 'imports.staff', icon: 'C' },
    { type: 'CLASSES', label: 'imports.classes', icon: 'S' },
    { type: 'PAYMENTS', label: 'imports.payments', icon: 'ₘ' },
    { type: 'OPENING_BALANCES', label: 'imports.balances', icon: 'Σ' },
  ];

  protected readonly step = signal(1);
  protected readonly selectedType = signal<ImportType>('STUDENTS');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly job = signal<ImportJob | null>(null);
  protected readonly preview = signal<ImportPreview | null>(null);
  protected readonly mapping = signal<Record<string, string>>({});
  protected readonly history = signal<readonly ImportJob[]>([]);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  constructor() {
    this.loadHistory();
  }

  protected chooseType(type: ImportType): void {
    this.selectedType.set(type);
    this.selectedFile.set(null);
  }

  protected chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
    this.failed.set(false);
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.run(this.api.upload(this.selectedType(), file), (job) => {
      this.job.set(job);
      this.mapping.set({ ...job.mapping });
      this.step.set(2);
      this.loadHistory();
    });
  }

  protected mapColumn(field: string, event: Event): void {
    const header = (event.target as HTMLSelectElement).value;
    this.mapping.update((current) => {
      const next = { ...current };
      if (header) next[field] = header;
      else delete next[field];
      return next;
    });
  }

  protected mappingComplete(): boolean {
    const job = this.job();
    if (!job) return false;
    return job.fields.filter((field) => field.required).every((field) => this.mapping()[field.key]);
  }

  protected saveMapping(): void {
    const job = this.job();
    if (!job || !this.mappingComplete()) return;
    this.run(this.api.map(job.id, this.mapping()), (updated) => {
      this.job.set(updated);
      this.step.set(3);
    });
  }

  protected validate(): void {
    const job = this.job();
    if (!job) return;
    this.run(this.api.validate(job.id), (preview) => {
      this.preview.set(preview);
      this.job.set(preview.job);
      this.step.set(4);
      this.loadHistory();
    });
  }

  protected execute(): void {
    const job = this.job();
    if (!job || job.invalidRows > 0) return;
    this.run(this.api.execute(job.id), (updated) => {
      this.job.set(updated);
      this.step.set(updated.status === 'IMPORTED' ? 5 : 4);
      this.failed.set(updated.status === 'FAILED');
      this.loadHistory();
    });
  }

  protected download(type: ImportType): void {
    this.failed.set(false);
    this.api
      .template(type)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `schoolz-import-${type.toLowerCase().replaceAll('_', '-')}.xlsx`;
          link.click();
          URL.revokeObjectURL(url);
        },
        error: () => this.failed.set(true),
      });
  }

  protected resume(selected: ImportJob): void {
    this.selectedType.set(selected.type);
    this.job.set(selected);
    this.mapping.set({ ...selected.mapping });
    this.preview.set(null);
    this.failed.set(selected.status === 'FAILED');
    if (selected.status === 'IMPORTED') {
      this.step.set(5);
    } else if (selected.status === 'VALIDATED') {
      this.step.set(4);
      this.api
        .preview(selected.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((preview) => this.preview.set(preview));
    } else {
      this.step.set(2);
    }
  }

  protected restart(): void {
    this.step.set(1);
    this.job.set(null);
    this.preview.set(null);
    this.mapping.set({});
    this.selectedFile.set(null);
    this.failed.set(false);
  }

  protected statusLabel(status: ImportJob['status']): TranslationKey {
    return `imports.status.${status.toLowerCase()}` as TranslationKey;
  }

  protected mappedCount(): number {
    return Object.keys(this.mapping()).length;
  }

  private loadHistory(): void {
    this.api
      .jobs()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (jobs) => this.history.set(jobs) });
  }

  private run<T>(request: Observable<T>, success: (value: T) => void): void {
    this.busy.set(true);
    this.failed.set(false);
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: success, error: () => this.failed.set(true) });
  }
}
