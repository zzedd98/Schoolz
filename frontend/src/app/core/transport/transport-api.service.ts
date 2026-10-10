import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TransportStop {
  readonly id: string;
  readonly sequence: number;
  readonly name: string;
  readonly address: string | null;
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly plannedOffsetMinutes: number;
}
export interface TransportRoute {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly active: boolean;
  readonly stops: readonly TransportStop[];
}
export interface TransportVehicle {
  readonly id: string;
  readonly code: string;
  readonly registrationNumber: string;
  readonly makeModel: string | null;
  readonly capacity: number;
  readonly status: string;
}
export interface TransportSubscription {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly routeName: string;
  readonly pickupStopName: string;
  readonly dropoffStopName: string;
  readonly morningEnabled: boolean;
  readonly eveningEnabled: boolean;
  readonly status: string;
}
export interface TransportEvent {
  readonly id: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly stopName: string;
  readonly source: string;
  readonly occurredAt: string;
}
export interface VehiclePosition {
  readonly providerKey: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly speedKph: number | null;
  readonly accuracyMeters: number | null;
  readonly recordedAt: string;
}
export interface TransportTrip {
  readonly id: string;
  readonly assignmentId: string;
  readonly routeName: string;
  readonly direction: string;
  readonly vehicleCode: string;
  readonly capacity: number;
  readonly driverName: string;
  readonly status: string;
  readonly gpsProviderKey: string;
  readonly startedAt: string | null;
  readonly onboardCount: number;
  readonly pickupEvents: readonly TransportEvent[];
  readonly dropoffEvents: readonly TransportEvent[];
  readonly latestPosition: VehiclePosition | null;
}
export interface TransportDashboard {
  readonly generatedAt: string;
  readonly vehicles: readonly TransportVehicle[];
  readonly drivers: readonly { id: string; displayName: string }[];
  readonly assistants: readonly { id: string; displayName: string }[];
  readonly routes: readonly TransportRoute[];
  readonly assignments: readonly {
    id: string;
    routeName: string;
    vehicleCode: string;
    driverName: string;
    assistantName: string | null;
    serviceDate: string;
    direction: string;
    plannedDepartureAt: string;
    status: string;
  }[];
  readonly subscriptions: readonly TransportSubscription[];
  readonly trips: readonly TransportTrip[];
  readonly students: readonly { id: string; displayName: string }[];
  readonly gpsProviders: readonly string[];
}
export interface FamilyTransportChild {
  readonly subscriptionId: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly routeName: string;
  readonly pickupStopName: string;
  readonly dropoffStopName: string;
  readonly morningEnabled: boolean;
  readonly eveningEnabled: boolean;
  readonly latestTrip: {
    id: string;
    status: string;
    direction: string;
    vehicleCode: string;
    registrationNumber: string;
    gpsProviderKey: string;
    startedAt: string;
  } | null;
  readonly pickup: { stopName: string; source: string; occurredAt: string } | null;
  readonly dropoff: { stopName: string; source: string; occurredAt: string } | null;
  readonly onboard: boolean;
  readonly message: string;
  readonly position: VehiclePosition | null;
  readonly eta: {
    available: boolean;
    minutes: number | null;
    providerKey: string | null;
    calculatedAt: string;
  };
}
export interface FamilyTransportDashboard {
  readonly generatedAt: string;
  readonly parentName: string;
  readonly children: readonly FamilyTransportChild[];
  readonly gpsProviders: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class TransportApiService {
  private readonly http = inject(HttpClient);
  dashboard(): Observable<TransportDashboard> {
    return this.http.get<TransportDashboard>('/api/v1/transport/dashboard');
  }
  familyDashboard(): Observable<FamilyTransportDashboard> {
    return this.http.get<FamilyTransportDashboard>('/api/v1/public/transport');
  }
  pickup(tripId: string, subscriptionId: string): Observable<TransportTrip> {
    return this.http.post<TransportTrip>(`/api/v1/transport/trips/${tripId}/pickups`, {
      subscriptionId,
      source: 'MANUAL',
    });
  }
  dropoff(tripId: string, subscriptionId: string): Observable<TransportTrip> {
    return this.http.post<TransportTrip>(`/api/v1/transport/trips/${tripId}/dropoffs`, {
      subscriptionId,
      source: 'MANUAL',
    });
  }
  position(tripId: string, latitude: number, longitude: number): Observable<TransportTrip> {
    return this.http.post<TransportTrip>(`/api/v1/transport/trips/${tripId}/positions`, {
      providerKey: 'local-demo',
      latitude,
      longitude,
      speedKph: 20,
      accuracyMeters: 8,
    });
  }
}
