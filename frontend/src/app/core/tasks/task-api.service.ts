import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';

export interface InternalTask {
  readonly id: string;
  readonly title: string;
  readonly description: string | null;
  readonly assigneeUserId: string;
  readonly assigneeName: string;
  readonly dueDate: string;
  readonly priority: TaskPriority;
  readonly status: TaskStatus;
  readonly linkedEntityType: string | null;
  readonly linkedEntityId: string | null;
  readonly routePath: string;
  readonly source: 'MANUAL' | 'WORKFLOW';
  readonly createdAt: string;
  readonly completedAt: string | null;
}

export interface TaskDashboard {
  readonly generatedAt: string;
  readonly page: number;
  readonly size: number;
  readonly hasNext: boolean;
  readonly counts: {
    readonly todo: number;
    readonly inProgress: number;
    readonly done: number;
    readonly overdue: number;
  };
  readonly assignees: readonly {
    readonly id: string;
    readonly displayName: string;
    readonly email: string;
  }[];
  readonly tasks: readonly InternalTask[];
}

export interface TaskCommand {
  title: string;
  description: string;
  assigneeUserId: string;
  dueDate: string;
  priority: TaskPriority;
  linkedEntityType?: string;
  linkedEntityId?: string;
}

@Injectable({ providedIn: 'root' })
export class TaskApiService {
  private readonly http = inject(HttpClient);

  dashboard(status = '', priority = '', page = 0, size = 20) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (priority) params = params.set('priority', priority);
    return this.http.get<TaskDashboard>('/api/v1/tasks', { params });
  }

  create(command: TaskCommand) {
    return this.http.post<InternalTask>('/api/v1/tasks', command);
  }

  status(taskId: string, status: TaskStatus) {
    return this.http.put<InternalTask>(`/api/v1/tasks/${taskId}/status`, { status });
  }

  assign(taskId: string, assigneeUserId: string) {
    return this.http.put<InternalTask>(`/api/v1/tasks/${taskId}/assignee`, { assigneeUserId });
  }
}
