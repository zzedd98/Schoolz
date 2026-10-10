import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface EthicalPolicy {
  readonly purpose: string;
  readonly prohibition: string;
  readonly factualOnly: boolean;
}

export interface EarlyWarningStatistics {
  readonly activeRules: number;
  readonly studentsObserved: number;
  readonly activeAlerts: number;
  readonly acknowledgedAlerts: number;
  readonly highAlerts: number;
}

export interface SignalDefinition {
  readonly code: string;
  readonly label: string;
  readonly unit: string;
  readonly sourceModule: string;
}

export interface RuleCondition {
  readonly id: string;
  readonly ruleId: string;
  readonly signalType: string;
  readonly signalLabel: string;
  readonly operator: string;
  readonly threshold: number;
  readonly order: number;
}

export interface EarlyWarningRule {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string;
  readonly severity: string;
  readonly matchMode: string;
  readonly active: boolean;
  readonly conditions: readonly RuleCondition[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface StudentSignal {
  readonly signalType: string;
  readonly label: string;
  readonly measuredOn: string;
  readonly value: number;
  readonly unit: string;
  readonly evidenceCount: number;
  readonly sourceModule: string;
  readonly factualDetail: string;
}

export interface ObservedStudent {
  readonly id: string;
  readonly identifier: string;
  readonly displayName: string;
  readonly classroomName: string;
  readonly signals: readonly StudentSignal[];
  readonly activeAlertCount: number;
  readonly highestSeverity: string;
}

export interface AlertEvidence {
  readonly id: string;
  readonly alertId: string;
  readonly signalType: string;
  readonly signalLabel: string;
  readonly actual: number;
  readonly threshold: number;
  readonly operator: string;
  readonly matched: boolean;
  readonly factualDetail: string;
}

export interface EarlyWarningAlert {
  readonly id: string;
  readonly ruleId: string;
  readonly ruleCode: string;
  readonly ruleName: string;
  readonly studentId: string;
  readonly studentIdentifier: string;
  readonly studentName: string;
  readonly status: string;
  readonly severity: string;
  readonly score: number;
  readonly factualSummary: string;
  readonly firstDetectedAt: string;
  readonly lastDetectedAt: string;
  readonly acknowledgedAt: string | null;
  readonly acknowledgementNote: string | null;
  readonly resolvedAt: string | null;
  readonly resolutionNote: string | null;
  readonly evidence: readonly AlertEvidence[];
}

export interface EvaluationHistory {
  readonly id: string;
  readonly evaluatedOn: string;
  readonly rulesEvaluated: number;
  readonly studentsEvaluated: number;
  readonly alertsCreated: number;
  readonly alertsRefreshed: number;
  readonly status: string;
  readonly completedAt: string;
}

export interface EarlyWarningDashboard {
  readonly generatedAt: string;
  readonly asOf: string;
  readonly ethicalPolicy: EthicalPolicy;
  readonly statistics: EarlyWarningStatistics;
  readonly signalDefinitions: readonly SignalDefinition[];
  readonly rules: readonly EarlyWarningRule[];
  readonly students: readonly ObservedStudent[];
  readonly alerts: readonly EarlyWarningAlert[];
  readonly evaluations: readonly EvaluationHistory[];
}

export interface RuleConditionCommand {
  signalType: string;
  operator: string;
  threshold: number;
}

export interface RuleCommand {
  code: string;
  name: string;
  description: string;
  severity: string;
  matchMode: string;
  active: boolean;
  conditions: RuleConditionCommand[];
}

@Injectable({ providedIn: 'root' })
export class EarlyWarningApiService {
  private readonly http = inject(HttpClient);

  dashboard(asOf: string) {
    return this.http.get<EarlyWarningDashboard>('/api/v1/early-warning/dashboard', {
      params: new HttpParams().set('asOf', asOf),
    });
  }

  evaluate(asOf: string) {
    return this.http.post<EarlyWarningDashboard>('/api/v1/early-warning/evaluate', null, {
      params: new HttpParams().set('asOf', asOf),
    });
  }

  createRule(command: RuleCommand) {
    return this.http.post<EarlyWarningRule>('/api/v1/early-warning/rules', command);
  }

  acknowledge(alertId: string, note: string) {
    return this.http.post<EarlyWarningAlert>(
      `/api/v1/early-warning/alerts/${alertId}/acknowledge`,
      { note },
    );
  }

  resolve(alertId: string, note: string) {
    return this.http.post<EarlyWarningAlert>(`/api/v1/early-warning/alerts/${alertId}/resolve`, {
      note,
    });
  }
}
