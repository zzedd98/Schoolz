import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';

import {
  EnrollmentApiService,
  EnrollmentClass,
  EnrollmentWorkflow,
} from '../../core/enrollments/enrollment-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-enrollment-admin',
  imports: [MatButtonModule],
  templateUrl: './enrollment-admin.component.html',
  styleUrl: './enrollment-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnrollmentAdminComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(EnrollmentApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly workflows = signal<readonly EnrollmentWorkflow[]>([]);
  protected readonly classes = signal<Record<string, readonly EnrollmentClass[]>>({});
  protected readonly selections = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected classOptions(workflowId: string): readonly EnrollmentClass[] {
    return this.classes()[workflowId] ?? [];
  }

  protected loadClasses(workflow: EnrollmentWorkflow): void {
    this.api
      .classes(workflow.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => {
          this.classes.update((current) => ({ ...current, [workflow.id]: options }));
          if (!this.selections()[workflow.id] && options[0]) {
            this.selections.update((current) => ({ ...current, [workflow.id]: options[0].id }));
          }
        },
        error: () => this.error.set(this.text('Classes indisponibles.', 'الأقسام غير متاحة.')),
      });
  }

  protected selectClass(workflowId: string, event: Event): void {
    const classroomId = (event.target as HTMLSelectElement).value;
    this.selections.update((current) => ({ ...current, [workflowId]: classroomId }));
  }

  protected assign(workflow: EnrollmentWorkflow): void {
    const classroomId = this.selections()[workflow.id];
    if (!classroomId) return;
    this.mutate(
      this.api.assignClass(workflow.id, classroomId),
      this.text('Classe affectée.', 'تم تعيين القسم.'),
    );
  }

  protected activate(workflow: EnrollmentWorkflow): void {
    this.mutate(
      this.api.activate(workflow.id),
      this.text('Inscription activée.', 'تم تفعيل التسجيل.'),
    );
  }

  protected readiness(workflow: EnrollmentWorkflow): number {
    const checks = [
      workflow.contract.status === 'ACCEPTED',
      workflow.familyConfirmed,
      workflow.services.confirmed,
      workflow.missingDocuments.length === 0,
      workflow.signed,
      workflow.feeStatus === 'PAID',
      workflow.classroomId !== null,
    ];
    return Math.round((checks.filter(Boolean).length / checks.length) * 100);
  }

  protected classAssignmentCount(): number {
    return this.workflows().filter((workflow) => workflow.currentStep === 'CLASS_ASSIGNMENT')
      .length;
  }

  protected readyCount(): number {
    return this.workflows().filter((workflow) => workflow.status === 'READY').length;
  }

  protected activeCount(): number {
    return this.workflows().filter((workflow) => workflow.status === 'ACTIVE').length;
  }

  protected load(): void {
    this.busy.set(true);
    this.api
      .adminWorkflows()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (workflows) => {
          this.workflows.set(workflows);
          for (const workflow of workflows.filter(
            (item) => item.currentStep === 'CLASS_ASSIGNMENT' || item.status === 'READY',
          ))
            this.loadClasses(workflow);
        },
        error: () => this.error.set(this.text('Chargement impossible.', 'تعذر التحميل.')),
      });
  }

  private mutate(request: ReturnType<EnrollmentApiService['activate']>, success: string): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.message.set(success);
          this.load();
        },
        error: () => this.error.set(this.text('Action impossible.', 'تعذر تنفيذ الإجراء.')),
      });
  }
}
