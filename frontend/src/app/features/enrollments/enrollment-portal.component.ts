import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin, Observable } from 'rxjs';

import {
  AdmissionPortalApiService,
  PortalApplication,
} from '../../core/admissions/admission-portal-api.service';
import {
  EnrollmentApiService,
  EnrollmentWorkflow,
} from '../../core/enrollments/enrollment-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-enrollment-portal',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './enrollment-portal.component.html',
  styleUrl: './enrollment-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnrollmentPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(EnrollmentApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly workflows = signal<readonly EnrollmentWorkflow[]>([]);
  protected readonly applications = signal<readonly PortalApplication[]>([]);
  protected readonly selectedId = signal('');
  protected readonly selected = computed(
    () => this.workflows().find((workflow) => workflow.id === this.selectedId()) ?? null,
  );
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');

  protected readonly familyForm = new FormGroup({
    guardianFirstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    guardianLastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    phone: new FormControl('', { nonNullable: true }),
    relationship: new FormControl('PARENT', { nonNullable: true }),
    addressLine1: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    addressLine2: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    region: new FormControl('', { nonNullable: true }),
    postalCode: new FormControl('', { nonNullable: true }),
  });

  protected readonly servicesForm = new FormGroup({
    transportSelected: new FormControl(false, { nonNullable: true }),
    transportRoute: new FormControl('', { nonNullable: true }),
    cafeteriaSelected: new FormControl(false, { nonNullable: true }),
    activities: new FormControl('', { nonNullable: true }),
    paymentPlan: new FormControl('MONTHLY', { nonNullable: true }),
  });

  protected readonly signatureForm = new FormGroup({
    signerName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accepted: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });

  protected readonly steps = [
    'CONTRACT',
    'FAMILY_INFORMATION',
    'SERVICES',
    'DOCUMENTS',
    'SIGNATURE',
    'REGISTRATION_FEE',
    'CLASS_ASSIGNMENT',
    'ACTIVE',
  ] as const;

  constructor() {
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected startableApplications(): readonly PortalApplication[] {
    const applicationIds = new Set(this.workflows().map((workflow) => workflow.applicationId));
    return this.applications().filter(
      (application) => application.status === 'ENROLLED' && !applicationIds.has(application.id),
    );
  }

  protected select(workflow: EnrollmentWorkflow): void {
    this.selectedId.set(workflow.id);
    const family = workflow.familyData;
    this.familyForm.patchValue({
      guardianFirstName: this.value(family, 'guardianFirstName'),
      guardianLastName: this.value(family, 'guardianLastName'),
      email: this.value(family, 'email'),
      phone: this.value(family, 'phone'),
      relationship: this.value(family, 'relationship') || 'PARENT',
      addressLine1: this.value(family, 'addressLine1'),
      addressLine2: this.value(family, 'addressLine2'),
      city: this.value(family, 'city'),
      region: this.value(family, 'region'),
      postalCode: this.value(family, 'postalCode'),
    });
    this.servicesForm.patchValue({
      transportSelected: workflow.services.transportSelected,
      transportRoute: workflow.services.transportRoute ?? '',
      cafeteriaSelected: workflow.services.cafeteriaSelected,
      activities: workflow.services.activities.join(', '),
      paymentPlan: workflow.services.paymentPlan,
    });
  }

  protected start(applicationId: string): void {
    this.run(
      this.api.startInitial(applicationId),
      this.text('Parcours créé.', 'تم إنشاء مسار التسجيل.'),
    );
  }

  protected acceptContract(workflow: EnrollmentWorkflow): void {
    this.run(this.api.acceptContract(workflow.id), this.text('Contrat accepté.', 'تم قبول العقد.'));
  }

  protected confirmFamily(workflow: EnrollmentWorkflow): void {
    if (this.familyForm.invalid) return;
    this.run(
      this.api.confirmFamily(workflow.id, this.familyForm.getRawValue()),
      this.text('Informations famille confirmées.', 'تم تأكيد معلومات الأسرة.'),
    );
  }

  protected confirmServices(workflow: EnrollmentWorkflow): void {
    const value = this.servicesForm.getRawValue();
    this.run(
      this.api.confirmServices(workflow.id, {
        ...value,
        activities: value.activities
          .split(',')
          .map((activity) => activity.trim())
          .filter(Boolean),
      }),
      this.text('Services enregistrés.', 'تم حفظ الخدمات.'),
    );
  }

  protected upload(workflow: EnrollmentWorkflow, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (!file) return;
    this.run(
      this.api.upload(workflow.id, 'IDENTITY_COPY', file),
      this.text('Document ajouté.', 'تمت إضافة الوثيقة.'),
    );
    input.value = '';
  }

  protected sign(workflow: EnrollmentWorkflow): void {
    if (this.signatureForm.invalid) return;
    this.run(
      this.api.sign(workflow.id, this.signatureForm.getRawValue().signerName),
      this.text('Contrat signé.', 'تم توقيع العقد.'),
    );
  }

  protected pay(workflow: EnrollmentWorkflow): void {
    this.run(
      this.api.pay(workflow.id),
      this.text('Paiement de démonstration validé.', 'تم تأكيد الدفع التجريبي.'),
    );
  }

  protected stepDone(workflow: EnrollmentWorkflow, step: string): boolean {
    return (
      this.steps.indexOf(workflow.currentStep as (typeof this.steps)[number]) >
      this.steps.indexOf(step as (typeof this.steps)[number])
    );
  }

  protected stepLabel(step: string): string {
    const labels: Record<string, readonly [string, string]> = {
      CONTRACT: ['Contrat', 'العقد'],
      FAMILY_INFORMATION: ['Famille', 'الأسرة'],
      SERVICES: ['Services', 'الخدمات'],
      DOCUMENTS: ['Documents', 'الوثائق'],
      SIGNATURE: ['Signature', 'التوقيع'],
      REGISTRATION_FEE: ['Frais', 'الرسوم'],
      CLASS_ASSIGNMENT: ['Classe', 'القسم'],
      ACTIVE: ['Active', 'مفعلة'],
    };
    return this.text(...(labels[step] ?? [step, step]));
  }

  private load(preferredId?: string): void {
    this.busy.set(true);
    this.error.set('');
    forkJoin({ workflows: this.api.portalWorkflows(), home: this.admissions.home() })
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ workflows, home }) => {
          this.workflows.set(workflows);
          this.applications.set(home.applications);
          const id = (preferredId ?? this.selectedId()) || workflows[0]?.id || '';
          const selected = workflows.find((workflow) => workflow.id === id) ?? workflows[0];
          if (selected) this.select(selected);
        },
        error: () =>
          this.error.set(
            this.text(
              'Connectez-vous d’abord au portail admissions avec le compte parent.',
              'سجّل الدخول أولاً إلى بوابة القبول بحساب ولي الأمر.',
            ),
          ),
      });
  }

  private run(request: Observable<EnrollmentWorkflow>, success: string): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (workflow) => {
          this.message.set(success);
          this.load(workflow.id);
        },
        error: () =>
          this.error.set(
            this.text('Cette étape n’a pas pu être enregistrée.', 'تعذر حفظ هذه المرحلة.'),
          ),
      });
  }

  private value(data: Readonly<Record<string, unknown>>, key: string): string {
    const value = data[key];
    return value === null || value === undefined ? '' : String(value);
  }
}
