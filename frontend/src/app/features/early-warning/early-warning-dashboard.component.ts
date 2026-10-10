import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  EarlyWarningAlert,
  EarlyWarningApiService,
  EarlyWarningDashboard,
  RuleCommand,
} from '../../core/early-warning/early-warning-api.service';

type Tab = 'alerts' | 'students' | 'rules' | 'history';
type ActionMode = 'acknowledge' | 'resolve';

@Component({
  selector: 'app-early-warning-dashboard',
  imports: [FormsModule],
  templateUrl: './early-warning-dashboard.component.html',
  styleUrl: './early-warning-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EarlyWarningDashboardComponent {
  private readonly api = inject(EarlyWarningApiService);

  protected readonly data = signal<EarlyWarningDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly evaluating = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly tab = signal<Tab>('alerts');
  protected readonly asOf = signal('2026-08-10');
  protected readonly selectedAlert = signal<EarlyWarningAlert | null>(null);
  protected readonly actionMode = signal<ActionMode>('acknowledge');
  protected readonly actionOpen = signal(false);
  protected readonly ruleOpen = signal(false);
  protected readonly activeAlerts = computed(
    () =>
      this.data()?.alerts.filter(
        (alert) => alert.status === 'OPEN' || alert.status === 'ACKNOWLEDGED',
      ) ?? [],
  );

  protected actionNote = '';
  protected ruleForm: RuleCommand = this.emptyRule();

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.dashboard(this.asOf()).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Le centre d’alertes ne peut pas être chargé pour le moment.');
        this.loading.set(false);
      },
    });
  }

  protected changeDate(event: Event): void {
    this.asOf.set((event.target as HTMLInputElement).value);
    this.load();
  }

  protected evaluate(): void {
    this.evaluating.set(true);
    this.success.set('');
    this.api.evaluate(this.asOf()).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.evaluating.set(false);
        this.success.set('Les règles ont été réévaluées sans dupliquer les alertes existantes.');
      },
      error: () => {
        this.evaluating.set(false);
        this.error.set('L’évaluation n’a pas pu être terminée.');
      },
    });
  }

  protected openAction(alert: EarlyWarningAlert, mode: ActionMode): void {
    this.selectedAlert.set(alert);
    this.actionMode.set(mode);
    this.actionNote = '';
    this.actionOpen.set(true);
  }

  protected submitAction(): void {
    const alert = this.selectedAlert();
    const note = this.actionNote.trim();
    if (!alert || !note) return;
    const request =
      this.actionMode() === 'acknowledge'
        ? this.api.acknowledge(alert.id, note)
        : this.api.resolve(alert.id, note);
    request.subscribe({
      next: () => {
        this.actionOpen.set(false);
        this.success.set(
          this.actionMode() === 'acknowledge'
            ? 'L’alerte est maintenant prise en charge.'
            : 'L’alerte est résolue ; son historique et ses preuves sont conservés.',
        );
        this.load();
      },
      error: () => this.error.set('Cette transition d’alerte n’a pas pu être enregistrée.'),
    });
  }

  protected openRule(): void {
    this.ruleForm = this.emptyRule();
    this.ruleOpen.set(true);
  }

  protected addCondition(): void {
    if (this.ruleForm.conditions.length >= 7) return;
    this.ruleForm.conditions = [
      ...this.ruleForm.conditions,
      { signalType: 'ABSENCE_RATE', operator: 'GT', threshold: 0 },
    ];
  }

  protected removeCondition(index: number): void {
    if (this.ruleForm.conditions.length === 1) return;
    this.ruleForm.conditions = this.ruleForm.conditions.filter((_, current) => current !== index);
  }

  protected createRule(): void {
    this.error.set('');
    this.api.createRule(this.ruleForm).subscribe({
      next: () => {
        this.ruleOpen.set(false);
        this.success.set('La règle factuelle est active et prête pour la prochaine évaluation.');
        this.load();
      },
      error: (response) => {
        this.error.set(
          response?.error?.detail ??
            'La règle a été refusée. Vérifiez les seuils et le vocabulaire factuel.',
        );
      },
    });
  }

  protected operator(value: string): string {
    return { GT: '>', GTE: '≥', LT: '<', LTE: '≤', EQ: '=' }[value] ?? value;
  }

  protected severity(value: string): string {
    return (
      { CRITICAL: 'Critique', HIGH: 'Élevée', MEDIUM: 'Modérée', LOW: 'À surveiller' }[value] ??
      value
    );
  }

  protected status(value: string): string {
    return (
      {
        OPEN: 'À traiter',
        ACKNOWLEDGED: 'Prise en charge',
        RESOLVED: 'Résolue',
        DISMISSED: 'Écartée',
      }[value] ?? value
    );
  }

  protected signalValue(value: number, unit: string): string {
    const formatted = new Intl.NumberFormat('fr-MA', { maximumFractionDigits: 1 }).format(value);
    return (
      {
        PERCENT: `${formatted} %`,
        SCORE_20: `${formatted}/20`,
        POINTS: `${formatted} pts`,
        MAD: `${formatted} MAD`,
        COUNT: formatted,
      }[unit] ?? `${formatted} ${unit}`
    );
  }

  protected dateTime(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  protected initials(name: string): string {
    return name
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  private emptyRule(): RuleCommand {
    return {
      code: '',
      name: '',
      description: '',
      severity: 'MEDIUM',
      matchMode: 'ALL',
      active: true,
      conditions: [{ signalType: 'ABSENCE_RATE', operator: 'GT', threshold: 10 }],
    };
  }
}
