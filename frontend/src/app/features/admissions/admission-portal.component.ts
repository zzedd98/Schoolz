import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';

import {
  AdmissionCatalog,
  AdmissionPortalApiService,
  PortalApplication,
  PortalHome,
} from '../../core/admissions/admission-portal-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admission-portal',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './admission-portal.component.html',
  styleUrl: './admission-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdmissionPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly tenantPreference = inject(TenantPreferenceService);
  protected readonly demoAccounts = environment.demoAccounts;
  private readonly initialTenant =
    this.route.snapshot.queryParamMap.get('tenant')?.trim().toLowerCase() ||
    this.tenantPreference.read() ||
    this.demoAccounts?.tenantSlug ||
    '';

  protected readonly tenantSlug = signal(this.initialTenant);
  protected readonly catalog = signal<AdmissionCatalog | null>(null);
  protected readonly home = signal<PortalHome | null>(null);
  protected readonly authMode = signal<'login' | 'register'>('login');
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly showChildForm = signal(false);
  protected readonly showApplicationForm = signal(false);
  protected readonly selectedApplicantId = signal('');
  protected readonly selectedCampaignId = signal('');
  protected readonly selectedCampusId = signal('');
  protected readonly selectedGradeId = signal('');
  protected readonly drafts = signal<Record<string, { motivation: string; notes: string }>>({});
  protected readonly documentTypes = signal<Record<string, string>>({});

  protected readonly loginForm = new FormGroup({
    tenantSlug: new FormControl(this.initialTenant, {
      nonNullable: true,
      validators: [Validators.required],
    }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly registerForm = new FormGroup({
    tenantSlug: new FormControl(this.initialTenant, {
      nonNullable: true,
      validators: [Validators.required],
    }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    phone: new FormControl('', { nonNullable: true }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
  });

  protected readonly childForm = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    birthDate: new FormControl('2019-05-12', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    gender: new FormControl('FEMALE', { nonNullable: true }),
    nationalityCode: new FormControl('MA', { nonNullable: true }),
    currentSchool: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.loadCatalog();
    this.refreshHome(false);
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected useDemo(): void {
    const demo = this.demoAccounts;
    if (!demo) return;
    this.loginForm.setValue({
      tenantSlug: demo.tenantSlug,
      email: demo.parent.email,
      password: demo.parent.password,
    });
  }

  protected login(): void {
    if (this.loginForm.invalid) return;
    const command = this.loginForm.getRawValue();
    this.tenantSlug.set(command.tenantSlug);
    this.run(this.api.login(command), () => {
      this.tenantPreference.remember(command.tenantSlug);
      this.loadCatalog();
      this.refreshHome(true);
    });
  }

  protected register(): void {
    if (this.registerForm.invalid) return;
    const command = this.registerForm.getRawValue();
    this.tenantSlug.set(command.tenantSlug);
    this.run(this.api.register(command), () => {
      this.tenantPreference.remember(command.tenantSlug);
      this.loadCatalog();
      this.refreshHome(true);
    });
  }

  protected logout(): void {
    this.run(this.api.logout(), () => {
      this.home.set(null);
      this.message.set(this.text('Vous êtes déconnecté.', 'تم تسجيل الخروج.'));
    });
  }

  protected addChild(): void {
    if (this.childForm.invalid) return;
    this.run(this.api.addApplicant(this.childForm.getRawValue()), () => {
      this.childForm.reset({
        birthDate: '2019-05-12',
        gender: 'FEMALE',
        nationalityCode: 'MA',
      });
      this.showChildForm.set(false);
      this.message.set(this.text('Enfant ajouté.', 'تمت إضافة الطفل.'));
      this.refreshHome(true);
    });
  }

  protected openApplicationForm(applicantId?: string): void {
    const catalog = this.catalog();
    const home = this.home();
    this.selectedApplicantId.set(applicantId ?? home?.applicants[0]?.id ?? '');
    this.selectedCampaignId.set(catalog?.campaigns[0]?.id ?? '');
    this.selectedCampusId.set(catalog?.campuses[0]?.id ?? '');
    this.selectedGradeId.set(catalog?.grades[0]?.id ?? '');
    this.showApplicationForm.set(true);
  }

  protected createApplication(): void {
    if (
      !this.selectedApplicantId() ||
      !this.selectedCampaignId() ||
      !this.selectedCampusId() ||
      !this.selectedGradeId()
    ) {
      this.error.set(this.text('Sélection incomplète.', 'الاختيارات غير مكتملة.'));
      return;
    }
    this.run(
      this.api.createApplication({
        campaignId: this.selectedCampaignId(),
        applicantId: this.selectedApplicantId(),
        desiredCampusId: this.selectedCampusId(),
        desiredGradeLevelId: this.selectedGradeId(),
      }),
      () => {
        this.showApplicationForm.set(false);
        this.message.set(this.text('Dossier créé.', 'تم إنشاء الملف.'));
        this.refreshHome(true);
      },
    );
  }

  protected draft(applicationId: string, field: 'motivation' | 'notes'): string {
    return this.drafts()[applicationId]?.[field] ?? '';
  }

  protected updateDraft(applicationId: string, field: 'motivation' | 'notes', event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.drafts.update((current) => ({
      ...current,
      [applicationId]: {
        motivation: current[applicationId]?.motivation ?? '',
        notes: current[applicationId]?.notes ?? '',
        [field]: value,
      },
    }));
  }

  protected saveForm(application: PortalApplication): void {
    const draft = this.drafts()[application.id];
    if (!draft?.motivation) {
      this.error.set(this.text('Expliquez votre motivation.', 'يرجى توضيح دوافعكم.'));
      return;
    }
    this.run(
      this.api.saveForm(application.id, {
        motivation: draft.motivation,
        familyNotes: draft.notes,
      }),
      () => {
        this.message.set(this.text('Formulaire enregistré.', 'تم حفظ الاستمارة.'));
        this.refreshHome(true);
      },
    );
  }

  protected documentType(applicationId: string): string {
    return this.documentTypes()[applicationId] ?? 'BIRTH_CERTIFICATE';
  }

  protected setDocumentType(applicationId: string, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.documentTypes.update((current) => ({ ...current, [applicationId]: value }));
  }

  protected upload(applicationId: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (!file) return;
    this.run(this.api.uploadDocument(applicationId, this.documentType(applicationId), file), () => {
      input.value = '';
      this.message.set(this.text('Pièce ajoutée.', 'تمت إضافة الوثيقة.'));
      this.refreshHome(true);
    });
  }

  protected pay(application: PortalApplication): void {
    this.run(this.api.pay(application.id, '4242'), () => {
      this.message.set(this.text('Paiement de démonstration validé.', 'تم تأكيد الدفع التجريبي.'));
      this.refreshHome(true);
    });
  }

  protected submit(application: PortalApplication): void {
    this.run(this.api.submit(application.id), () => {
      this.message.set(this.text('Dossier envoyé à l’école.', 'تم إرسال الملف إلى المدرسة.'));
      this.refreshHome(true);
    });
  }

  protected acceptOffer(application: PortalApplication): void {
    if (!application.offer) return;
    this.run(this.api.acceptOffer(application.offer.id), () => {
      this.message.set(
        this.text(
          'Offre acceptée. Bienvenue chez SchoolZ !',
          'تم قبول العرض. مرحباً بكم في SchoolZ!',
        ),
      );
      this.refreshHome(true);
    });
  }

  protected markRead(notificationId: string): void {
    this.api
      .markRead(notificationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.refreshHome(true) });
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, readonly [string, string]> = {
      APPLICATION_STARTED: ['Dossier commencé', 'بدأ الملف'],
      APPLICATION_COMPLETE: ['Dossier complet', 'ملف مكتمل'],
      TEST_SCHEDULED: ['Test planifié', 'اختبار مبرمج'],
      REVIEW: ['En revue', 'قيد المراجعة'],
      ACCEPTED: ['Accepté', 'مقبول'],
      WAITLISTED: ['Liste d’attente', 'لائحة الانتظار'],
      REJECTED: ['Refusé', 'مرفوض'],
      ENROLLED: ['Inscription confirmée', 'تم تأكيد التسجيل'],
    };
    const label = labels[status];
    return label ? this.text(...label) : status;
  }

  private loadCatalog(): void {
    if (!this.tenantSlug()) return;
    this.api
      .catalog(this.tenantSlug())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (catalog) => this.catalog.set(catalog) });
  }

  private refreshHome(showErrors: boolean): void {
    this.api
      .home()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (home) => {
          this.home.set(home);
          this.seedDrafts(home);
        },
        error: () => {
          if (showErrors) {
            this.error.set(
              this.text('Session expirée. Reconnectez-vous.', 'انتهت الجلسة. يرجى تسجيل الدخول.'),
            );
          }
        },
      });
  }

  private seedDrafts(home: PortalHome): void {
    const drafts: Record<string, { motivation: string; notes: string }> = {};
    for (const application of home.applications) {
      drafts[application.id] = {
        motivation: String(application.formData['motivation'] ?? ''),
        notes: String(application.formData['familyNotes'] ?? ''),
      };
    }
    this.drafts.set(drafts);
  }

  private run<T>(request: Observable<T>, success: (value: T) => void): void {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: success,
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text('L’opération n’a pas pu aboutir.', 'تعذر إتمام العملية.'),
          ),
      });
  }
}
