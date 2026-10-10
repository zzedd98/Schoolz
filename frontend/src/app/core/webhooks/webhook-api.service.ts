import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface WebhookSubscription {
  readonly id: string;
  readonly name: string;
  readonly endpointUrl: string;
  readonly eventTypes: readonly string[];
  readonly secretPrefix: string;
  readonly maxAttempts: number;
  readonly timeoutSeconds: number;
  readonly active: boolean;
  readonly createdAt: string;
  readonly revokedAt: string | null;
}

export interface WebhookDelivery {
  readonly id: string;
  readonly eventId: string;
  readonly subscriptionId: string;
  readonly subscriptionName: string;
  readonly endpointUrl: string;
  readonly eventType: string;
  readonly idempotencyKey: string;
  readonly status: 'PENDING' | 'RETRY_SCHEDULED' | 'DELIVERED' | 'DEAD_LETTER';
  readonly attemptCount: number;
  readonly nextAttemptAt: string | null;
  readonly deliveredAt: string | null;
  readonly lastHttpStatus: number | null;
  readonly lastError: string | null;
  readonly updatedAt: string;
}

export interface WebhookDashboard {
  readonly generatedAt: string;
  readonly statistics: {
    readonly subscriptionCount: number;
    readonly activeCount: number;
    readonly deliveredCount: number;
    readonly retryCount: number;
    readonly deadLetterCount: number;
  };
  readonly eventTypes: readonly string[];
  readonly subscriptions: readonly WebhookSubscription[];
  readonly deliveries: readonly WebhookDelivery[];
  readonly attempts: readonly {
    readonly id: string;
    readonly deliveryId: string;
    readonly eventType: string;
    readonly subscriptionName: string;
    readonly attemptNumber: number;
    readonly requestId: string;
    readonly signatureTimestamp: number;
    readonly signature: string;
    readonly httpStatus: number | null;
    readonly outcome: string;
    readonly error: string | null;
    readonly durationMs: number;
    readonly attemptedAt: string;
  }[];
  readonly deadLetters: readonly {
    readonly id: string;
    readonly deliveryId: string;
    readonly eventType: string;
    readonly subscriptionName: string;
    readonly reason: string;
    readonly failedAt: string;
    readonly resolvedAt: string | null;
    readonly resolutionNote: string | null;
  }[];
}

export interface WebhookSubscriptionCommand {
  name: string;
  endpointUrl: string;
  eventTypes: string[];
  maxAttempts: number;
  timeoutSeconds: number;
}

@Injectable({ providedIn: 'root' })
export class WebhookApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<WebhookDashboard>('/api/v1/webhooks/dashboard');
  }

  create(command: WebhookSubscriptionCommand) {
    return this.http.post<{
      readonly subscription: WebhookSubscription;
      readonly signingSecret: string;
    }>('/api/v1/webhooks/subscriptions', command);
  }

  revoke(subscriptionId: string, reason: string) {
    return this.http.post<WebhookSubscription>(
      `/api/v1/webhooks/subscriptions/${subscriptionId}/revoke`,
      { reason },
    );
  }

  publish(eventType: string, idempotencyKey: string, payload: Record<string, unknown>) {
    return this.http.post<{
      readonly id: string;
      readonly deliveryCount: number;
    }>('/api/v1/webhooks/events', { eventType, idempotencyKey, payload });
  }

  processDue() {
    return this.http.post<{
      readonly processed: number;
      readonly delivered: number;
      readonly deferred: number;
      readonly deadLettered: number;
    }>('/api/v1/webhooks/deliveries/process-due', {});
  }

  retry(deliveryId: string, reason: string) {
    return this.http.post<WebhookDelivery>(`/api/v1/webhooks/deliveries/${deliveryId}/retry`, {
      reason,
    });
  }
}
