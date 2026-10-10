import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import {
  HrAttendance,
  HrDashboard,
  HrEmployee,
  HrEmployeeRecord,
  HrLeaveRequest,
  HumanResourcesApiService,
} from '../../core/hr/human-resources-api.service';

type HrView = 'team' | 'leaves' | 'attendance' | 'structure';
type EmployeeTab = 'contract' | 'schedule' | 'documents' | 'qualifications';

@Component({
  selector: 'app-human-resources-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './human-resources-dashboard.component.html',
  styleUrl: './human-resources-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HumanResourcesDashboardComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(HumanResourcesApiService);

  protected readonly dashboard = signal<HrDashboard | null>(null);
  protected readonly record = signal<HrEmployeeRecord | null>(null);
  protected readonly activeView = signal<HrView>('team');
  protected readonly employeeTab = signal<EmployeeTab>('contract');
  protected readonly selectedEmployeeId = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly recordLoading = signal(false);
  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  protected readonly feedback = signal<string | null>(null);
  protected readonly leaveDrawerOpen = signal(false);
  protected readonly structureDrawer = signal<'department' | 'position' | null>(null);

  protected attendanceDate = '';
  protected leaveDraft = {
    employeeId: '',
    leaveType: 'ANNUAL',
    startsOn: '2026-09-21',
    endsOn: '2026-09-22',
    workingDays: 2,
    reason: '',
  };
  protected departmentDraft = { code: '', name: '', description: '' };
  protected positionDraft = {
    departmentId: '',
    code: '',
    title: '',
    description: '',
    teacherPosition: false,
  };

  ngOnInit(): void {
    this.load();
  }

  protected load(date?: string, preserveEmployee = false): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.dashboard(date).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.attendanceDate = dashboard.attendanceDate;
        this.loading.set(false);
        const employeeId =
          preserveEmployee && this.selectedEmployeeId()
            ? this.selectedEmployeeId()
            : dashboard.employees[0]?.id;
        if (employeeId) this.openEmployee(employeeId);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  protected openEmployee(employeeId: string): void {
    this.selectedEmployeeId.set(employeeId);
    this.recordLoading.set(true);
    this.api.employee(employeeId).subscribe({
      next: (record) => {
        this.record.set(record);
        this.recordLoading.set(false);
      },
      error: () => this.recordLoading.set(false),
    });
  }

  protected openLeaveDrawer(employee?: HrEmployee): void {
    const employeeId = employee?.id ?? this.dashboard()?.employees[0]?.id ?? '';
    this.leaveDraft = { ...this.leaveDraft, employeeId, reason: '' };
    this.leaveDrawerOpen.set(true);
  }

  protected submitLeave(): void {
    if (!this.leaveDraft.employeeId || !this.leaveDraft.startsOn || !this.leaveDraft.endsOn) return;
    this.saving.set(true);
    this.api.requestLeave(this.leaveDraft).subscribe({
      next: () => {
        this.leaveDrawerOpen.set(false);
        this.finish('Demande enregistrée au statut « reçue ».');
      },
      error: () => this.finish('La demande n’a pas pu être enregistrée.', false),
    });
  }

  protected managerReview(request: HrLeaveRequest): void {
    this.saving.set(true);
    this.api.managerReview(request.id).subscribe({
      next: () => this.finish('Demande transmise en revue manager.'),
      error: () => this.finish('Transition de workflow refusée.', false),
    });
  }

  protected decide(request: HrLeaveRequest, decision: 'APPROVED' | 'REJECTED'): void {
    this.saving.set(true);
    const comment = decision === 'APPROVED' ? 'Planning compatible.' : 'Organisation à revoir.';
    this.api.decideLeave(request.id, decision, comment).subscribe({
      next: () => this.finish(decision === 'APPROVED' ? 'Congé approuvé.' : 'Congé refusé.'),
      error: () => this.finish('La décision n’a pas pu être enregistrée.', false),
    });
  }

  protected markAttendance(employee: HrEmployee, status: HrAttendance['status']): void {
    this.saving.set(true);
    this.api
      .recordAttendance({
        employeeId: employee.id,
        workDate: this.attendanceDate,
        status,
        source: 'ADMIN',
      })
      .subscribe({
        next: () => this.finish(`Présence de ${employee.displayName} mise à jour.`),
        error: () => this.finish('La présence n’a pas pu être mise à jour.', false),
      });
  }

  protected attendanceFor(employeeId: string): HrAttendance | undefined {
    return this.dashboard()?.attendance.find((entry) => entry.employeeId === employeeId);
  }

  protected createDepartment(): void {
    if (!this.departmentDraft.code || !this.departmentDraft.name) return;
    this.saving.set(true);
    this.api.createDepartment(this.departmentDraft).subscribe({
      next: () => {
        this.structureDrawer.set(null);
        this.departmentDraft = { code: '', name: '', description: '' };
        this.finish('Département créé.');
      },
      error: () => this.finish('Création du département impossible.', false),
    });
  }

  protected createPosition(): void {
    if (!this.positionDraft.code || !this.positionDraft.title) return;
    this.saving.set(true);
    const command = {
      ...this.positionDraft,
      departmentId: this.positionDraft.departmentId || undefined,
    };
    this.api.createPosition(command).subscribe({
      next: () => {
        this.structureDrawer.set(null);
        this.finish('Poste créé.');
      },
      error: () => this.finish('Création du poste impossible.', false),
    });
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      ACTIVE: 'Actif',
      ON_LEAVE: 'En congé',
      REQUESTED: 'Reçue',
      MANAGER_REVIEW: 'Revue manager',
      APPROVED: 'Approuvée',
      REJECTED: 'Refusée',
      PRESENT: 'Présent',
      REMOTE: 'À distance',
      LEAVE: 'Congé',
      ABSENT: 'Absent',
      REST_DAY: 'Repos',
    };
    return labels[status] ?? status;
  }

  protected pendingLeaves(): number {
    return (
      this.dashboard()?.leaveRequests.filter((entry) =>
        ['REQUESTED', 'MANAGER_REVIEW'].includes(entry.status),
      ).length ?? 0
    );
  }

  protected presentToday(): number {
    return (
      this.dashboard()?.attendance.filter((entry) => ['PRESENT', 'REMOTE'].includes(entry.status))
        .length ?? 0
    );
  }

  protected initials(name: string): string {
    return name
      .split(' ')
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }

  private finish(message: string, reload = true): void {
    this.saving.set(false);
    this.feedback.set(message);
    if (reload) this.load(this.attendanceDate, true);
    window.setTimeout(() => this.feedback.set(null), 3200);
  }
}
