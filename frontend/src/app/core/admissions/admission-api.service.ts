import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type AdmissionStage =
  | 'NEW_LEAD'
  | 'CONTACTED'
  | 'SCHOOL_VISIT'
  | 'APPLICATION_STARTED'
  | 'APPLICATION_COMPLETE'
  | 'TEST_SCHEDULED'
  | 'REVIEW'
  | 'ACCEPTED'
  | 'WAITLISTED'
  | 'REJECTED'
  | 'ENROLLED';

export interface AdmissionStep {
  readonly code: string;
  readonly name: string;
  readonly instructions: string | null;
  readonly sequenceNumber: number;
  readonly required: boolean;
}

export interface AdmissionCampaign {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly opensOn: string;
  readonly closesOn: string;
  readonly applicationFee: number;
  readonly currencyCode: string;
  readonly requiredDocuments: readonly string[];
  readonly status: string;
  readonly steps: readonly AdmissionStep[];
}

export interface BoardCard {
  readonly id: string;
  readonly cardType: 'PROSPECT' | 'APPLICATION';
  readonly primaryName: string;
  readonly secondaryName: string | null;
  readonly reference: string | null;
  readonly status: AdmissionStage;
  readonly source: string;
  readonly grade: string | null;
  readonly completenessPercent: number;
  readonly email: string | null;
  readonly phone: string | null;
  readonly createdAt: string;
}

export interface BoardColumn {
  readonly status: AdmissionStage;
  readonly cards: readonly BoardCard[];
}

export interface AdmissionBoard {
  readonly campaignId: string;
  readonly columns: readonly BoardColumn[];
}

export interface SourceMetric {
  readonly source: string;
  readonly count: number;
}

export interface AdmissionDashboard {
  readonly prospects: number;
  readonly applications: number;
  readonly conversionRate: number;
  readonly sources: readonly SourceMetric[];
  readonly incompleteApplications: number;
  readonly acceptances: number;
  readonly enrollments: number;
  readonly acceptanceRate: number;
}

export interface CampaignCommand {
  readonly code: string;
  readonly name: string;
  readonly description: string;
  readonly opensOn: string;
  readonly closesOn: string;
  readonly applicationFee: number;
  readonly currencyCode: string;
  readonly requiredDocuments: readonly string[];
  readonly status: 'DRAFT' | 'OPEN' | 'CLOSED';
}

export interface ProspectCommand {
  readonly campaignId: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly phone: string;
  readonly childName: string;
  readonly desiredGradeCode: string;
  readonly source: string;
  readonly notes: string;
}

@Injectable({ providedIn: 'root' })
export class AdmissionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/admissions';

  campaigns(): Observable<readonly AdmissionCampaign[]> {
    return this.http.get<readonly AdmissionCampaign[]>(`${this.base}/campaigns`);
  }

  createCampaign(command: CampaignCommand): Observable<AdmissionCampaign> {
    return this.http.post<AdmissionCampaign>(`${this.base}/campaigns`, command);
  }

  board(campaignId: string): Observable<AdmissionBoard> {
    return this.http.get<AdmissionBoard>(`${this.base}/board`, { params: { campaignId } });
  }

  dashboard(campaignId: string): Observable<AdmissionDashboard> {
    return this.http.get<AdmissionDashboard>(`${this.base}/dashboard`, {
      params: { campaignId },
    });
  }

  move(card: BoardCard, targetStatus: AdmissionStage): Observable<void> {
    return this.http.patch<void>(`${this.base}/pipeline`, {
      cardType: card.cardType,
      cardId: card.id,
      targetStatus,
      comment: 'Déplacement depuis le Kanban',
    });
  }

  createProspect(command: ProspectCommand): Observable<unknown> {
    return this.http.post(`${this.base}/prospects`, command);
  }
}
