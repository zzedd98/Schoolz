import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import {
  InternalTask,
  TaskApiService,
  TaskCommand,
  TaskDashboard,
  TaskPriority,
  TaskStatus,
} from '../../core/tasks/task-api.service';

@Component({
  selector: 'app-task-center',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './task-center.component.html',
  styleUrl: './task-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TaskCenterComponent {
  private readonly api = inject(TaskApiService);
  protected readonly auth = inject(AuthService);
  protected readonly data = signal<TaskDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly drawerOpen = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected status = '';
  protected priority = '';
  protected form: TaskCommand = this.emptyForm();

  protected readonly statuses = [
    { value: '', label: 'Actives' },
    { value: 'TODO', label: 'À démarrer' },
    { value: 'IN_PROGRESS', label: 'En cours' },
    { value: 'DONE', label: 'Terminées' },
  ];

  constructor() {
    this.load();
  }

  protected load(page = 0): void {
    this.loading.set(true);
    this.api.dashboard(this.status, this.priority, page).subscribe({
      next: (result) => {
        this.data.set(result);
        if (!this.form.assigneeUserId && result.assignees.length) {
          this.form.assigneeUserId = result.assignees[0].id;
        }
        this.loading.set(false);
      },
      error: (response) => this.fail(response, 'Les tâches ne peuvent pas être chargées.'),
    });
  }

  protected filter(status: string): void {
    this.status = status;
    this.load();
  }

  protected filterPriority(priority: string): void {
    this.priority = priority;
    this.load();
  }

  protected openCreator(): void {
    this.form = this.emptyForm();
    this.form.assigneeUserId = this.data()?.assignees[0]?.id ?? '';
    this.drawerOpen.set(true);
  }

  protected create(): void {
    this.busy.set(true);
    this.api.create(this.form).subscribe({
      next: () => {
        this.busy.set(false);
        this.drawerOpen.set(false);
        this.message.set('La tâche a été confiée.');
        this.load();
      },
      error: (response) => this.fail(response, 'La tâche ne peut pas être créée.'),
    });
  }

  protected move(task: InternalTask, status: TaskStatus): void {
    this.busy.set(true);
    this.api.status(task.id, status).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set(status === 'DONE' ? 'Tâche terminée.' : 'Tâche mise à jour.');
        this.load(this.data()?.page ?? 0);
      },
      error: (response) => this.fail(response, 'Le statut ne peut pas être modifié.'),
    });
  }

  protected priorityName(priority: TaskPriority): string {
    return { LOW: 'Basse', MEDIUM: 'Normale', HIGH: 'Haute', URGENT: 'Urgente' }[priority];
  }

  protected statusName(status: TaskStatus): string {
    return { TODO: 'À démarrer', IN_PROGRESS: 'En cours', DONE: 'Terminée', CANCELLED: 'Annulée' }[
      status
    ];
  }

  private fail(response: { error?: { detail?: string } } | null, fallback: string): void {
    this.loading.set(false);
    this.busy.set(false);
    this.error.set(response?.error?.detail ?? fallback);
  }

  private emptyForm(): TaskCommand {
    const due = new Date();
    due.setDate(due.getDate() + 2);
    return {
      title: '',
      description: '',
      assigneeUserId: '',
      dueDate: due.toISOString().slice(0, 10),
      priority: 'MEDIUM',
    };
  }
}
