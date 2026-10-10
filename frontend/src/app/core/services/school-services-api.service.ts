import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
export interface CafeteriaDashboard {
  generatedAt: string;
  plans: readonly {
    id: string;
    name: string;
    type: string;
    monthlyPrice: number | null;
    singleMealPrice: number | null;
  }[];
  menus: readonly {
    id: string;
    serviceDate: string;
    mealType: string;
    title: string;
    description: string;
    published: boolean;
  }[];
  subscriptions: readonly {
    id: string;
    studentName: string;
    planName: string;
    status: string;
    billingMode: string;
  }[];
  attendances: readonly {
    id: string;
    studentName: string;
    menuTitle: string;
    serviceDate: string;
    attendanceType: string;
    occurredAt: string;
  }[];
  restrictions: readonly {
    id: string;
    studentName: string;
    type: string;
    allergen: string | null;
    details: string;
    severity: string;
    emergencyAction: string;
  }[];
  healthDataVisible: boolean;
}
export interface ActivityDashboard {
  generatedAt: string;
  activities: readonly {
    id: string;
    code: string;
    name: string;
    category: string;
    description: string;
    coachName: string;
    allowParentEnrollment: boolean;
    price: number;
    status: string;
    maximumCapacity: number;
    waitingListEnabled: boolean;
    enrolledCount: number;
    waitingCount: number;
    sessions: readonly {
      id: string;
      startsAt: string;
      endsAt: string;
      location: string;
      status: string;
    }[];
  }[];
  students: readonly { id: string; displayName: string }[];
  enrollments: readonly {
    id: string;
    activityName: string;
    studentName: string;
    status: string;
    enrolledAt: string;
  }[];
}
@Injectable({ providedIn: 'root' })
export class SchoolServicesApiService {
  private readonly http = inject(HttpClient);
  cafeteria(internal = false): Observable<CafeteriaDashboard> {
    return this.http.get<CafeteriaDashboard>(
      internal ? '/api/v1/cafeteria/dashboard' : '/api/v1/public/cafeteria',
    );
  }
  activities(internal = false): Observable<ActivityDashboard> {
    return this.http.get<ActivityDashboard>(
      internal ? '/api/v1/activities/dashboard' : '/api/v1/public/activities',
    );
  }
  enroll(
    activityId: string,
    studentId: string,
  ): Observable<{ status: string; waitingPosition: number | null }> {
    return this.http.post<{ status: string; waitingPosition: number | null }>(
      '/api/v1/public/activities/enrollments',
      { activityId, studentId },
    );
  }
}
