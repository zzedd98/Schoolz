import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface HrDepartment {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly campusId: string | null;
  readonly active: boolean;
  readonly employeeCount: number;
}

export interface HrPosition {
  readonly id: string;
  readonly code: string;
  readonly title: string;
  readonly description: string | null;
  readonly departmentId: string | null;
  readonly departmentName: string | null;
  readonly teacherPosition: boolean;
  readonly active: boolean;
}

export interface HrEmployee {
  readonly id: string;
  readonly personId: string;
  readonly staffProfileId: string;
  readonly employeeNumber: string;
  readonly displayName: string;
  readonly jobTitle: string;
  readonly departmentId: string | null;
  readonly departmentName: string | null;
  readonly positionId: string | null;
  readonly positionTitle: string | null;
  readonly teacherPosition: boolean;
  readonly managerEmployeeId: string | null;
  readonly managerName: string | null;
  readonly workEmail: string | null;
  readonly workPhone: string | null;
  readonly status: 'ACTIVE' | 'ON_LEAVE' | 'SUSPENDED' | 'ENDED';
  readonly joinedOn: string;
  readonly endedOn: string | null;
}

export interface HrContract {
  readonly id: string;
  readonly contractNumber: string;
  readonly contractType: string;
  readonly startsOn: string;
  readonly endsOn: string | null;
  readonly weeklyHours: number | null;
  readonly status: string;
  readonly notes: string | null;
}

export interface HrSchedule {
  readonly id: string;
  readonly dayOfWeek: number;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly breakMinutes: number;
  readonly effectiveFrom: string;
  readonly effectiveTo: string | null;
}

export interface HrLeaveRequest {
  readonly id: string;
  readonly employeeId: string;
  readonly employeeName: string;
  readonly leaveType: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly workingDays: number;
  readonly reason: string | null;
  readonly status: 'REQUESTED' | 'MANAGER_REVIEW' | 'APPROVED' | 'REJECTED';
  readonly requestedAt: string;
  readonly managerReviewedAt: string | null;
  readonly decidedAt: string | null;
  readonly decisionComment: string | null;
}

export interface HrAttendance {
  readonly id: string;
  readonly employeeId: string;
  readonly employeeName: string;
  readonly workDate: string;
  readonly checkedInAt: string | null;
  readonly checkedOutAt: string | null;
  readonly status: 'PRESENT' | 'REMOTE' | 'LEAVE' | 'ABSENT' | 'REST_DAY';
  readonly source: string;
  readonly note: string | null;
}

export interface HrStaffDocument {
  readonly id: string;
  readonly documentType: string;
  readonly fileName: string;
  readonly objectKey: string;
  readonly issuedOn: string | null;
  readonly expiresOn: string | null;
  readonly status: string;
  readonly createdAt: string;
}

export interface HrQualification {
  readonly id: string;
  readonly qualificationType: string;
  readonly title: string;
  readonly institution: string | null;
  readonly subjectArea: string | null;
  readonly obtainedOn: string | null;
  readonly expiresOn: string | null;
  readonly verified: boolean;
  readonly verifiedAt: string | null;
}

export interface HrDashboard {
  readonly generatedAt: string;
  readonly attendanceDate: string;
  readonly departments: readonly HrDepartment[];
  readonly positions: readonly HrPosition[];
  readonly employees: readonly HrEmployee[];
  readonly leaveRequests: readonly HrLeaveRequest[];
  readonly attendance: readonly HrAttendance[];
  readonly expiringDocuments: number;
}

export interface HrEmployeeRecord {
  readonly employee: HrEmployee;
  readonly contracts: readonly HrContract[];
  readonly schedules: readonly HrSchedule[];
  readonly leaveRequests: readonly HrLeaveRequest[];
  readonly attendance: readonly HrAttendance[];
  readonly documents: readonly HrStaffDocument[];
  readonly qualifications: readonly HrQualification[];
}

@Injectable({ providedIn: 'root' })
export class HumanResourcesApiService {
  private readonly http = inject(HttpClient);

  dashboard(date?: string) {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<HrDashboard>('/api/v1/hr/dashboard', { params });
  }

  employee(employeeId: string) {
    return this.http.get<HrEmployeeRecord>(`/api/v1/hr/employees/${employeeId}`);
  }

  requestLeave(command: {
    employeeId: string;
    leaveType: string;
    startsOn: string;
    endsOn: string;
    workingDays: number;
    reason: string;
  }) {
    return this.http.post<HrLeaveRequest>('/api/v1/hr/leave-requests', command);
  }

  managerReview(requestId: string) {
    return this.http.post<HrLeaveRequest>(
      `/api/v1/hr/leave-requests/${requestId}/manager-review`,
      {},
    );
  }

  decideLeave(requestId: string, decision: 'APPROVED' | 'REJECTED', comment: string) {
    return this.http.post<HrLeaveRequest>(`/api/v1/hr/leave-requests/${requestId}/decision`, {
      decision,
      comment,
    });
  }

  recordAttendance(command: {
    employeeId: string;
    workDate: string;
    status: HrAttendance['status'];
    source: string;
    note?: string;
  }) {
    return this.http.post<HrAttendance>('/api/v1/hr/attendance', command);
  }

  createDepartment(command: { code: string; name: string; description?: string }) {
    return this.http.post<HrDepartment>('/api/v1/hr/departments', command);
  }

  createPosition(command: {
    departmentId?: string;
    code: string;
    title: string;
    description?: string;
    teacherPosition: boolean;
  }) {
    return this.http.post<HrPosition>('/api/v1/hr/positions', command);
  }
}
