import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type CommunicationCategory = 'ANNOUNCEMENT' | 'DIRECT_MESSAGE' | 'AUTOMATED_NOTIFICATION';
export type CommunicationChannel = 'IN_APP' | 'PUSH' | 'EMAIL' | 'SMS' | 'WHATSAPP';

export interface CampusOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

export interface ClassroomOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly campusId: string;
  readonly campusName: string;
}

export interface ParentOption {
  readonly portalAccountId: string;
  readonly displayName: string;
  readonly email: string;
  readonly studentId: string;
  readonly studentName: string;
}

export interface NotificationTemplate {
  readonly id: string;
  readonly key: string;
  readonly locale: string;
  readonly channel: CommunicationChannel;
  readonly category: CommunicationCategory;
  readonly subject: string;
  readonly body: string;
  readonly active: boolean;
}

export interface Announcement {
  readonly id: string;
  readonly title: string;
  readonly body: string;
  readonly audienceScope: 'SCHOOL' | 'CAMPUS' | 'CLASS';
  readonly campusId: string | null;
  readonly campusName: string | null;
  readonly classroomId: string | null;
  readonly classroomName: string | null;
  readonly createdBy: string;
  readonly publishedAt: string;
  readonly recipientCount: number;
}

export interface CommunicationMessage {
  readonly id: string;
  readonly senderKind: 'INTERNAL' | 'PARENT' | 'SYSTEM';
  readonly senderName: string;
  readonly body: string;
  readonly sentAt: string;
}

export interface Conversation {
  readonly id: string;
  readonly subject: string;
  readonly portalAccountId: string;
  readonly parentName: string;
  readonly studentId: string | null;
  readonly studentName: string | null;
  readonly classroomId: string | null;
  readonly classroomName: string | null;
  readonly internalName: string;
  readonly status: 'OPEN' | 'CLOSED';
  readonly lastMessageAt: string;
  readonly messages: readonly CommunicationMessage[];
}

export interface NotificationDelivery {
  readonly channel: CommunicationChannel;
  readonly status: 'DELIVERED' | 'PREVIEW' | 'SKIPPED' | 'FAILED';
  readonly adapterKey: string;
  readonly recipientMasked: string | null;
  readonly attemptedAt: string;
  readonly errorMessage: string | null;
}

export interface CommunicationNotification {
  readonly id: string;
  readonly category: CommunicationCategory;
  readonly title: string;
  readonly body: string;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly createdAt: string;
  readonly readAt: string | null;
  readonly deliveries: readonly NotificationDelivery[];
}

export interface NotificationPreference {
  readonly category: CommunicationCategory;
  readonly channel: CommunicationChannel;
  readonly enabled: boolean;
}

export interface CommunicationDashboard {
  readonly generatedAt: string;
  readonly teacherView: boolean;
  readonly campuses: readonly CampusOption[];
  readonly classrooms: readonly ClassroomOption[];
  readonly parents: readonly ParentOption[];
  readonly templates: readonly NotificationTemplate[];
  readonly announcements: readonly Announcement[];
  readonly conversations: readonly Conversation[];
  readonly notifications: readonly CommunicationNotification[];
}

export interface FamilyCommunicationDashboard {
  readonly generatedAt: string;
  readonly parentName: string;
  readonly announcements: readonly Announcement[];
  readonly conversations: readonly Conversation[];
  readonly notifications: readonly CommunicationNotification[];
  readonly preferences: readonly NotificationPreference[];
}

@Injectable({ providedIn: 'root' })
export class CommunicationsApiService {
  private readonly http = inject(HttpClient);
  private readonly internalUrl = '/api/v1/communications';
  private readonly familyUrl = '/api/v1/public/communications';

  dashboard(): Observable<CommunicationDashboard> {
    return this.http.get<CommunicationDashboard>(`${this.internalUrl}/dashboard`);
  }

  publishAnnouncement(command: {
    title: string;
    body: string;
    audienceScope: 'SCHOOL' | 'CAMPUS' | 'CLASS';
    campusId: string | null;
    classroomId: string | null;
  }): Observable<Announcement> {
    return this.http.post<Announcement>(`${this.internalUrl}/announcements`, command);
  }

  startConversation(command: {
    portalAccountId: string;
    studentId: string | null;
    subject: string;
    body: string;
  }): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.internalUrl}/conversations`, command);
  }

  reply(conversationId: string, body: string): Observable<Conversation> {
    return this.http.post<Conversation>(
      `${this.internalUrl}/conversations/${conversationId}/messages`,
      { body },
    );
  }

  sendAutomated(command: {
    templateKey: string;
    portalAccountId: string;
    studentId: string | null;
    variables: Readonly<Record<string, string>>;
  }): Observable<CommunicationNotification> {
    return this.http.post<CommunicationNotification>(
      `${this.internalUrl}/automated-notifications`,
      command,
    );
  }

  readInternal(notificationId: string): Observable<void> {
    return this.http.patch<void>(`${this.internalUrl}/notifications/${notificationId}/read`, null);
  }

  familyDashboard(): Observable<FamilyCommunicationDashboard> {
    return this.http.get<FamilyCommunicationDashboard>(this.familyUrl);
  }

  familyReply(conversationId: string, body: string): Observable<Conversation> {
    return this.http.post<Conversation>(
      `${this.familyUrl}/conversations/${conversationId}/messages`,
      { body },
    );
  }

  updatePreferences(
    preferences: readonly NotificationPreference[],
  ): Observable<readonly NotificationPreference[]> {
    return this.http.put<readonly NotificationPreference[]>(`${this.familyUrl}/preferences`, {
      preferences,
    });
  }

  readFamily(notificationId: string): Observable<void> {
    return this.http.patch<void>(`${this.familyUrl}/notifications/${notificationId}/read`, null);
  }
}
