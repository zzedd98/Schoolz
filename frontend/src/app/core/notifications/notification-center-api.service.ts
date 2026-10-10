import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type NotificationState = 'UNREAD' | 'READ' | 'ARCHIVED';
export type NotificationChannel = 'PUSH' | 'EMAIL' | 'SMS';

export interface NotificationCenterItem {
  readonly id: string;
  readonly category: string;
  readonly title: string;
  readonly body: string;
  readonly critical: boolean;
  readonly alertKey: string | null;
  readonly state: NotificationState;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly routePath: string;
  readonly createdAt: string;
  readonly readAt: string | null;
  readonly archivedAt: string | null;
}

export interface NotificationPreference {
  readonly channel: NotificationChannel;
  readonly enabled: boolean;
  readonly mandatory: boolean;
}

export interface NotificationCenterResponse {
  readonly generatedAt: string;
  readonly status: 'ALL' | NotificationState;
  readonly page: number;
  readonly size: number;
  readonly hasNext: boolean;
  readonly counts: { readonly unread: number; readonly read: number; readonly archived: number };
  readonly preferences: readonly NotificationPreference[];
  readonly items: readonly NotificationCenterItem[];
}

@Injectable({ providedIn: 'root' })
export class NotificationCenterApiService {
  private readonly http = inject(HttpClient);

  center(status = 'ALL', page = 0, size = 20) {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<NotificationCenterResponse>('/api/v1/notification-center', { params });
  }

  read(notificationId: string) {
    return this.http.patch<NotificationCenterItem>(
      `/api/v1/notification-center/${notificationId}/read`,
      {},
    );
  }

  archive(notificationId: string) {
    return this.http.patch<NotificationCenterItem>(
      `/api/v1/notification-center/${notificationId}/archive`,
      {},
    );
  }

  preferences(channels: Readonly<Record<NotificationChannel, boolean>>) {
    return this.http.put<readonly NotificationPreference[]>(
      '/api/v1/notification-center/preferences',
      { channels },
    );
  }
}
