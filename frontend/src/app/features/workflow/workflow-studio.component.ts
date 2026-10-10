import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  WorkflowApiService,
  WorkflowCommand,
  WorkflowDashboard,
  WorkflowExecution,
} from '../../core/workflow/workflow-api.service';

type StudioTab = 'workflows' | 'executions';

@Component({
  selector: 'app-workflow-studio',
  imports: [FormsModule],
  templateUrl: './workflow-studio.component.html',
  styleUrl: './workflow-studio.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkflowStudioComponent {
  private readonly api = inject(WorkflowApiService);

  protected readonly data = signal<WorkflowDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly tab = signal<StudioTab>('workflows');
  protected readonly builderOpen = signal(false);
  protected readonly eventOpen = signal(false);
  protected readonly executionOpen = signal(false);
  protected readonly selectedExecution = signal<WorkflowExecution | null>(null);
  protected readonly busy = signal(false);
  protected readonly recentExecutions = computed(() => this.data()?.executions.slice(0, 12) ?? []);

  protected workflowForm: WorkflowCommand = this.emptyWorkflow();
  protected eventType = 'invoice.overdue';
  protected eventDays = 12;
  protected eventUnjustified = true;

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
        this.error.set('Le studio de workflows ne peut pas être chargé pour le moment.');
        this.loading.set(false);
      },
    });
  }

  protected toggle(id: string, active: boolean): void {
    this.api.activate(id, active).subscribe({
      next: () => {
        this.success.set(
          active ? 'Le workflow est actif.' : 'Le workflow est maintenant en pause.',
        );
        this.load();
      },
      error: () => this.error.set('Le statut du workflow n’a pas pu être modifié.'),
    });
  }

  protected openBuilder(): void {
    this.workflowForm = this.emptyWorkflow();
    this.builderOpen.set(true);
  }

  protected addCondition(): void {
    this.workflowForm.conditions = [
      ...this.workflowForm.conditions,
      { fieldPath: '', operator: 'EQ', expectedValue: '', valueType: 'STRING' },
    ];
  }

  protected removeCondition(index: number): void {
    this.workflowForm.conditions = this.workflowForm.conditions.filter((_, i) => i !== index);
  }

  protected addAction(): void {
    this.workflowForm.actions = [
      ...this.workflowForm.actions,
      { actionType: 'CREATE_TASK', label: '', configuration: {} },
    ];
  }

  protected removeAction(index: number): void {
    if (this.workflowForm.actions.length === 1) return;
    this.workflowForm.actions = this.workflowForm.actions.filter((_, i) => i !== index);
  }

  protected create(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.create(this.workflowForm).subscribe({
      next: () => {
        this.busy.set(false);
        this.builderOpen.set(false);
        this.success.set('Le nouveau workflow a été enregistré avec sa chaîne complète.');
        this.load();
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(response?.error?.detail ?? 'Le workflow est incomplet ou invalide.');
      },
    });
  }

  protected simulate(): void {
    this.busy.set(true);
    const payload =
      this.eventType === 'student.absence.created'
        ? { student: 'Yasmine Alaoui', unjustified: this.eventUnjustified }
        : this.eventType === 'invoice.overdue'
          ? { invoiceId: 'FAC-DEMO-LIVE', days: this.eventDays, amount: 1500 }
          : { demo: true };
    this.api
      .dispatch({
        eventId: crypto.randomUUID(),
        eventType: this.eventType,
        sourceModule: 'workflow-studio',
        payload,
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.eventOpen.set(false);
          this.success.set(
            `${result.completed} exécuté(s), ${result.skipped} ignoré(s), ${result.loopBlocked} boucle(s) bloquée(s).`,
          );
          this.tab.set('executions');
          this.load();
        },
        error: () => {
          this.busy.set(false);
          this.error.set('L’événement de test n’a pas pu être distribué.');
        },
      });
  }

  protected inspect(execution: WorkflowExecution): void {
    this.selectedExecution.set(execution);
    this.executionOpen.set(true);
  }

  protected operator(value: string): string {
    return (
      { EQ: '=', NE: '≠', GT: '>', GTE: '≥', LT: '<', LTE: '≤', EXISTS: 'existe', IN: 'dans' }[
        value
      ] ?? value
    );
  }

  protected actionName(value: string): string {
    return (
      {
        SEND_NOTIFICATION: 'Notification',
        SEND_EMAIL: 'E-mail',
        CREATE_TASK: 'Tâche',
        NOTIFY_PARENT: 'Parent',
        EMIT_EVENT: 'Événement',
      }[value] ?? value
    );
  }

  protected status(value: string): string {
    return (
      {
        COMPLETED: 'Exécuté',
        SKIPPED: 'Conditions non remplies',
        LOOP_BLOCKED: 'Boucle bloquée',
        FAILED: 'Échec',
      }[value] ?? value
    );
  }

  protected dateTime(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', { dateStyle: 'medium', timeStyle: 'short' }).format(
      new Date(value),
    );
  }

  protected payload(value: Readonly<Record<string, unknown>>): string {
    return Object.entries(value)
      .map(([key, item]) => `${key}: ${String(item)}`)
      .join(' · ');
  }

  private emptyWorkflow(): WorkflowCommand {
    return {
      code: '',
      name: '',
      description: '',
      active: true,
      maxDepth: 6,
      trigger: { eventType: 'invoice.overdue', sourceModule: 'billing' },
      conditions: [{ fieldPath: 'days', operator: 'GT', expectedValue: '7', valueType: 'NUMBER' }],
      actions: [
        {
          actionType: 'CREATE_TASK',
          label: 'Créer une tâche de suivi',
          configuration: { adapter: 'local' },
        },
      ],
    };
  }
}
