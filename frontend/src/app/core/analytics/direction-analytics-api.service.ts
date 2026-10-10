import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface CampusOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly city: string;
}

export interface DirectionSummary {
  readonly snapshotDate: string | null;
  readonly totalStudents: number;
  readonly presentStudents: number;
  readonly absentStudents: number;
  readonly teachersAbsent: number;
  readonly expectedRevenue: number;
  readonly collectedRevenue: number;
  readonly outstandingRevenue: number;
  readonly newApplications: number;
  readonly capacityTotal: number;
  readonly enrolledStudents: number;
  readonly absenceRate: number;
  readonly collectionRate: number;
  readonly capacityRate: number;
  readonly currencyCode: string;
}

export interface EnrollmentPoint {
  readonly date: string;
  readonly totalStudents: number;
  readonly newApplications: number;
}

export interface CollectionPoint {
  readonly date: string;
  readonly expected: number;
  readonly collected: number;
  readonly outstanding: number;
  readonly collectionRate: number;
}

export interface AttendancePoint {
  readonly date: string;
  readonly present: number;
  readonly absent: number;
  readonly absenceRate: number;
}

export interface DimensionValue {
  readonly metricCode: string;
  readonly code: string;
  readonly labelFr: string;
  readonly labelAr: string | null;
  readonly value: number;
  readonly target: number | null;
  readonly order: number;
}

export interface CampusComparison {
  readonly campusId: string;
  readonly campusCode: string;
  readonly campusName: string;
  readonly city: string;
  readonly totalStudents: number;
  readonly presentStudents: number;
  readonly absentStudents: number;
  readonly expectedRevenue: number;
  readonly collectedRevenue: number;
  readonly outstandingRevenue: number;
  readonly capacityTotal: number;
  readonly capacityRate: number;
  readonly collectionRate: number;
}

export interface DirectionDashboard {
  readonly generatedAt: string;
  readonly asOf: string;
  readonly selectedCampusId: string | null;
  readonly campuses: readonly CampusOption[];
  readonly summary: DirectionSummary;
  readonly enrollmentTrend: readonly EnrollmentPoint[];
  readonly collectionTrend: readonly CollectionPoint[];
  readonly absenteeismTrend: readonly AttendancePoint[];
  readonly classDistribution: readonly DimensionValue[];
  readonly admissionsFunnel: readonly DimensionValue[];
  readonly revenueByService: readonly DimensionValue[];
  readonly unpaidAging: readonly DimensionValue[];
  readonly campusComparison: readonly CampusComparison[];
  readonly insights: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class DirectionAnalyticsApiService {
  private readonly http = inject(HttpClient);

  direction(asOf: string, campusId?: string) {
    let params = new HttpParams().set('asOf', asOf);
    if (campusId) params = params.set('campusId', campusId);
    return this.http.get<DirectionDashboard>('/api/v1/analytics/direction', { params });
  }
}
