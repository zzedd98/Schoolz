import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface WorkflowStatistics {
  readonly workflowCount: number;
  readonly activeWorkflows: number;
  readonly completedExecutions: number;
  readonly skippedExecutions: number;
  readonly loopBlocks: number;
  readonly actionsExecuted: number;
}

export interface WorkflowTrigger {
  readonly id: string;
  readonly eventType: string;
  readonly sourceModule: string;
}

export interface WorkflowCondition {
  readonly id: string;
  readonly fieldPath: string;
  readonly operator: string;
  readonly expectedValue: string | null;
  readonly valueType: string;
  readonly order: number;
}

export interface WorkflowAction {
  readonly id: string;
  readonly actionType: string;
  readonly label: string;
  readonly configuration: Readonly<Record<string, unknown>>;
  readonly emittedEventType: string | null;
  readonly order: number;
}

export interface WorkflowDefinition {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string;
  readonly active: boolean;
  readonly maxDepth: number;
  readonly trigger: WorkflowTrigger;
  readonly conditions: readonly WorkflowCondition[];
  readonly actions: readonly WorkflowAction[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface WorkflowExecutionLog {
  readonly id: string;
  readonly sequence: number;
  readonly level: string;
  readonly stepType: string;
  readonly message: string;
  readonly detail: Readonly<Record<string, unknown>>;
  readonly occurredAt: string;
}

export interface WorkflowExecution {
  readonly id: string;
  readonly workflowId: string;
  readonly workflowCode: string;
  readonly workflowName: string;
  readonly eventId: string;
  readonly rootEventId: string;
  readonly parentExecutionId: string | null;
  readonly eventType: string;
  readonly status: string;
  readonly depth: number;
  readonly payload: Readonly<Record<string, unknown>>;
  readonly startedAt: string;
  readonly completedAt: string;
  readonly failureReason: string | null;
  readonly logs: readonly WorkflowExecutionLog[];
}

export interface WorkflowDashboard {
  readonly generatedAt: string;
  readonly statistics: WorkflowStatistics;
  readonly workflows: readonly WorkflowDefinition[];
  readonly executions: readonly WorkflowExecution[];
  readonly supportedEvents: readonly string[];
  readonly supportedActions: readonly string[];
  readonly loopPolicy: {
    readonly maximumDepth: number;
    readonly maximumExecutionsPerRoot: number;
    readonly ancestryGuard: boolean;
  };
}

export interface WorkflowCommand {
  code: string;
  name: string;
  description: string;
  active: boolean;
  maxDepth: number;
  trigger: { eventType: string; sourceModule: string };
  conditions: {
    fieldPath: string;
    operator: string;
    expectedValue: string;
    valueType: string;
  }[];
  actions: {
    actionType: string;
    label: string;
    configuration: Readonly<Record<string, unknown>>;
    emittedEventType?: string | null;
  }[];
}

export interface WorkflowEventCommand {
  readonly eventId: string;
  readonly eventType: string;
  readonly sourceModule: string;
  readonly payload: Readonly<Record<string, unknown>>;
}

export interface WorkflowDispatch {
  readonly rootEventId: string;
  readonly completed: number;
  readonly skipped: number;
  readonly loopBlocked: number;
  readonly executions: readonly WorkflowExecution[];
}

@Injectable({ providedIn: 'root' })
export class WorkflowApiService {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<WorkflowDashboard> {
    return this.http.get<WorkflowDashboard>('/api/v1/workflows/dashboard');
  }

  create(command: WorkflowCommand): Observable<WorkflowDefinition> {
    return this.http.post<WorkflowDefinition>('/api/v1/workflows', command);
  }

  activate(workflowId: string, active: boolean): Observable<WorkflowDefinition> {
    return this.http.put<WorkflowDefinition>(`/api/v1/workflows/${workflowId}/activation`, {
      active,
    });
  }

  dispatch(command: WorkflowEventCommand): Observable<WorkflowDispatch> {
    return this.http.post<WorkflowDispatch>('/api/v1/workflows/events', command);
  }
}
