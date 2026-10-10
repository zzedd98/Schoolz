import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { finalize, forkJoin } from 'rxjs';

import {
  AdmissionApiService,
  AdmissionBoard,
  AdmissionCampaign,
  AdmissionDashboard,
  AdmissionStage,
  BoardCard,
} from '../../core/admissions/admission-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-admission-crm',
  imports: [MatButtonModule, ReactiveFormsModule],
  templateUrl: './admission-crm.component.html',
  styleUrl: './admission-crm.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdmissionCrmComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(AdmissionApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly campaigns = signal<readonly AdmissionCampaign[]>([]);
  protected readonly selectedCampaignId = signal('');
  protected readonly board = signal<AdmissionBoard | null>(null);
  protected readonly dashboard = signal<AdmissionDashboard | null>(null);
  protected readonly dragged = signal<BoardCard | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly showCampaignForm = signal(false);
  protected readonly showProspectForm = signal(false);

  protected readonly campaignForm = new FormGroup({
    code: new FormControl('ADM-2027', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('Admissions 2027–2028', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    description: new FormControl('Une nouvelle campagne d’admission SchoolZ.', {
      nonNullable: true,
    }),
    opensOn: new FormControl(this.dateOffset(0), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    closesOn: new FormControl(this.dateOffset(365), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    applicationFee: new FormControl(500, { nonNullable: true, validators: [Validators.min(0)] }),
  });

  protected readonly prospectForm = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    phone: new FormControl('', { nonNullable: true }),
    childName: new FormControl('', { nonNullable: true }),
    desiredGradeCode: new FormControl('', { nonNullable: true }),
    source: new FormControl('WEBSITE', { nonNullable: true }),
    notes: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.loadCampaigns();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected stageLabel(stage: AdmissionStage): string {
    const labels: Record<AdmissionStage, readonly [string, string]> = {
      NEW_LEAD: ['Nouveau contact', 'جهة اتصال جديدة'],
      CONTACTED: ['Contacté', 'تم التواصل'],
      SCHOOL_VISIT: ['Visite école', 'زيارة المدرسة'],
      APPLICATION_STARTED: ['Dossier commencé', 'بدأ الملف'],
      APPLICATION_COMPLETE: ['Dossier complet', 'ملف مكتمل'],
      TEST_SCHEDULED: ['Test planifié', 'اختبار مبرمج'],
      REVIEW: ['En revue', 'قيد المراجعة'],
      ACCEPTED: ['Accepté', 'مقبول'],
      WAITLISTED: ['Liste d’attente', 'لائحة الانتظار'],
      REJECTED: ['Refusé', 'مرفوض'],
      ENROLLED: ['Inscrit', 'مسجل'],
    };
    return this.text(...labels[stage]);
  }

  protected selectCampaign(event: Event): void {
    this.selectedCampaignId.set((event.target as HTMLSelectElement).value);
    this.refresh();
  }

  protected beginDrag(card: BoardCard): void {
    this.dragged.set(card);
  }

  protected allowDrop(event: DragEvent): void {
    event.preventDefault();
  }

  protected drop(stage: AdmissionStage): void {
    const card = this.dragged();
    this.dragged.set(null);
    if (!card || card.status === stage || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .move(card, stage)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.refresh(),
        error: () =>
          this.error.set(
            this.text(
              'Ce déplacement ne respecte pas la prochaine étape du pipeline.',
              'هذا النقل لا يحترم المرحلة التالية من المسار.',
            ),
          ),
      });
  }

  protected createCampaign(): void {
    if (this.campaignForm.invalid || this.busy()) return;
    const value = this.campaignForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.api
      .createCampaign({
        ...value,
        currencyCode: 'MAD',
        requiredDocuments: ['BIRTH_CERTIFICATE', 'REPORT_CARD'],
        status: 'OPEN',
      })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (campaign) => {
          this.showCampaignForm.set(false);
          this.loadCampaigns(campaign.id);
        },
        error: () =>
          this.error.set(this.text('Impossible de créer la campagne.', 'تعذر إنشاء الحملة.')),
      });
  }

  protected createProspect(): void {
    if (this.prospectForm.invalid || !this.selectedCampaignId() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .createProspect({
        campaignId: this.selectedCampaignId(),
        ...this.prospectForm.getRawValue(),
      })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.prospectForm.reset({ source: 'WEBSITE' });
          this.showProspectForm.set(false);
          this.refresh();
        },
        error: () =>
          this.error.set(this.text('Impossible d’ajouter le prospect.', 'تعذر إضافة المهتم.')),
      });
  }

  protected campaignName(): string {
    return (
      this.campaigns().find((campaign) => campaign.id === this.selectedCampaignId())?.name ?? '—'
    );
  }

  private loadCampaigns(preferredId?: string): void {
    this.api
      .campaigns()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (campaigns) => {
          this.campaigns.set(campaigns);
          const id = preferredId ?? this.selectedCampaignId() ?? campaigns[0]?.id ?? '';
          this.selectedCampaignId.set(id || campaigns[0]?.id || '');
          this.refresh();
        },
        error: () => this.error.set(this.text('Chargement impossible.', 'تعذر التحميل.')),
      });
  }

  private refresh(): void {
    const campaignId = this.selectedCampaignId();
    if (!campaignId) return;
    this.busy.set(true);
    forkJoin({ board: this.api.board(campaignId), dashboard: this.api.dashboard(campaignId) })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ board, dashboard }) => {
          this.board.set(board);
          this.dashboard.set(dashboard);
        },
        error: () => this.error.set(this.text('Actualisation impossible.', 'تعذر التحديث.')),
      });
  }

  private dateOffset(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
  }
}
