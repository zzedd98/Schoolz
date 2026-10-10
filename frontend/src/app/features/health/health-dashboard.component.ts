import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  HealthApiService,
  HealthDashboard,
  HealthStudentSummary,
  StudentHealthRecord,
} from '../../core/health/health-api.service';

type HealthSection =
  'overview' | 'allergies' | 'medications' | 'incidents' | 'visits' | 'vaccinations';
type EntryMode = 'profile' | 'allergy' | 'medication' | 'incident' | 'visit' | 'vaccination';

@Component({
  selector: 'app-health-dashboard',
  imports: [DatePipe, FormsModule],
  templateUrl: './health-dashboard.component.html',
  styleUrl: './health-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HealthDashboardComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(HealthApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dashboard = signal<HealthDashboard | null>(null);
  protected readonly record = signal<StudentHealthRecord | null>(null);
  protected readonly selectedStudentId = signal<string | null>(null);
  protected readonly activeSection = signal<HealthSection>('overview');
  protected readonly entryMode = signal<EntryMode | null>(null);
  protected readonly loading = signal(true);
  protected readonly recordLoading = signal(false);
  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  protected readonly feedback = signal<string | null>(null);

  protected profileBloodGroup = '';
  protected profileInsuranceProvider = '';
  protected profileInsuranceNumber = '';
  protected profilePhysicianName = '';
  protected profilePhysicianPhone = '';
  protected profileEmergencyNotes = '';

  protected allergyName = '';
  protected allergyReaction = '';
  protected allergySeverity = 'MODERATE';
  protected allergyInstructions = '';

  protected medicationName = '';
  protected medicationDosage = '';
  protected medicationRoute = '';
  protected medicationSchedule = '';
  protected medicationStartsOn = this.today();
  protected medicationEndsOn = '';
  protected medicationPrescriber = '';
  protected medicationAuthorization = '';
  protected medicationInstructions = '';

  protected incidentType = 'SCHOOL_INCIDENT';
  protected incidentSeverity = 'LOW';
  protected incidentLocation = '';
  protected incidentDescription = '';
  protected incidentAction = '';
  protected incidentStatus = 'OPEN';

  protected visitReason = '';
  protected visitObservations = '';
  protected visitTemperature: number | null = null;
  protected visitAction = '';
  protected visitReturnToClass = true;
  protected visitReferredTo = '';

  protected vaccineName = '';
  protected vaccineDose = '';
  protected vaccineDate = this.today();
  protected vaccineProvider = '';
  protected vaccineDocument = '';
  protected vaccineNextDue = '';
  protected vaccineStatus = 'RECORDED';

  protected readonly sections: readonly { id: HealthSection; label: string }[] = [
    { id: 'overview', label: 'Synthèse' },
    { id: 'allergies', label: 'Allergies' },
    { id: 'medications', label: 'Médicaments' },
    { id: 'incidents', label: 'Incidents' },
    { id: 'visits', label: 'Visites' },
    { id: 'vaccinations', label: 'Vaccinations' },
  ];

  constructor() {
    this.loadDashboard();
  }

  protected selectStudent(student: HealthStudentSummary): void {
    this.selectedStudentId.set(student.studentId);
    this.activeSection.set('overview');
    this.entryMode.set(null);
    this.loadStudent(student.studentId);
  }

  protected openEntry(mode: EntryMode): void {
    this.feedback.set(null);
    this.entryMode.set(mode);
  }

  protected saveCurrentEntry(): void {
    const studentId = this.selectedStudentId();
    const mode = this.entryMode();
    if (!studentId || !mode || this.saving()) return;
    this.saving.set(true);
    this.feedback.set(null);

    const request: Observable<unknown> =
      mode === 'profile'
        ? this.api.saveProfile(studentId, {
            bloodGroup: this.profileBloodGroup,
            insuranceProvider: this.profileInsuranceProvider,
            insuranceNumber: this.profileInsuranceNumber,
            physicianName: this.profilePhysicianName,
            physicianPhone: this.profilePhysicianPhone,
            emergencyNotes: this.profileEmergencyNotes,
          })
        : mode === 'allergy'
          ? this.api.addAllergy(studentId, {
              allergen: this.allergyName,
              reactionDescription: this.allergyReaction,
              severity: this.allergySeverity,
              emergencyInstructions: this.allergyInstructions,
              active: true,
            })
          : mode === 'medication'
            ? this.api.addMedication(studentId, {
                medicationName: this.medicationName,
                dosageText: this.medicationDosage,
                administrationRoute: this.medicationRoute,
                scheduleText: this.medicationSchedule,
                startsOn: this.medicationStartsOn || null,
                endsOn: this.medicationEndsOn || null,
                prescriberName: this.medicationPrescriber,
                authorizationReference: this.medicationAuthorization,
                instructions: this.medicationInstructions,
                active: true,
              })
            : mode === 'incident'
              ? this.api.addIncident(studentId, {
                  occurredAt: new Date().toISOString(),
                  incidentType: this.incidentType,
                  severity: this.incidentSeverity,
                  location: this.incidentLocation,
                  description: this.incidentDescription,
                  actionTaken: this.incidentAction,
                  status: this.incidentStatus,
                })
              : mode === 'visit'
                ? this.api.addVisit(studentId, {
                    visitedAt: new Date().toISOString(),
                    reason: this.visitReason,
                    observations: this.visitObservations,
                    temperatureCelsius: this.visitTemperature,
                    actionTaken: this.visitAction,
                    returnToClass: this.visitReturnToClass,
                    referredTo: this.visitReferredTo,
                  })
                : this.api.addVaccination(studentId, {
                    vaccineName: this.vaccineName,
                    doseLabel: this.vaccineDose,
                    administeredOn: this.vaccineDate,
                    providerName: this.vaccineProvider,
                    documentReference: this.vaccineDocument,
                    nextDueOn: this.vaccineNextDue || null,
                    status: this.vaccineStatus,
                  });

    request
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set('Enregistrement sécurisé effectué.');
          this.entryMode.set(null);
          this.resetEntry(mode);
          this.loadDashboard(studentId);
        },
        error: () => this.feedback.set('La saisie n’a pas pu être enregistrée.'),
      });
  }

  protected toggleVaccinationPolicy(): void {
    const dashboard = this.dashboard();
    if (!dashboard || this.saving()) return;
    this.saving.set(true);
    this.api
      .updatePolicy(!dashboard.policy.vaccinationTrackingEnabled)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.loadDashboard(this.selectedStudentId()),
        error: () => this.feedback.set('La politique de santé n’a pas pu être modifiée.'),
      });
  }

  protected countProfiles(): number {
    return this.dashboard()?.students.filter((student) => student.profileReady).length ?? 0;
  }

  protected countAllergies(): number {
    return (
      this.dashboard()?.students.reduce((total, student) => total + student.activeAllergies, 0) ?? 0
    );
  }

  protected countOpenIncidents(): number {
    return (
      this.dashboard()?.students.reduce((total, student) => total + student.openIncidents, 0) ?? 0
    );
  }

  protected severityLabel(severity: string): string {
    return (
      { LOW: 'Faible', MODERATE: 'Modérée', HIGH: 'Élevée', CRITICAL: 'Critique' }[severity] ??
      severity
    );
  }

  private loadDashboard(preferredStudentId: string | null = null): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .dashboard()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          const selected =
            dashboard.students.find((student) => student.studentId === preferredStudentId) ??
            dashboard.students[0];
          if (selected) this.selectStudent(selected);
        },
        error: () => this.failed.set(true),
      });
  }

  private loadStudent(studentId: string): void {
    this.recordLoading.set(true);
    this.api
      .student(studentId)
      .pipe(
        finalize(() => this.recordLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (record) => {
          this.record.set(record);
          this.profileBloodGroup = record.profile.bloodGroup ?? '';
          this.profileInsuranceProvider = record.profile.insuranceProvider ?? '';
          this.profileInsuranceNumber = record.profile.insuranceNumber ?? '';
          this.profilePhysicianName = record.profile.physicianName ?? '';
          this.profilePhysicianPhone = record.profile.physicianPhone ?? '';
          this.profileEmergencyNotes = record.profile.emergencyNotes ?? '';
        },
        error: () => this.feedback.set('Le dossier sensible n’a pas pu être chargé.'),
      });
  }

  private resetEntry(mode: EntryMode): void {
    if (mode === 'allergy') {
      this.allergyName = '';
      this.allergyReaction = '';
      this.allergyInstructions = '';
    } else if (mode === 'medication') {
      this.medicationName = '';
      this.medicationDosage = '';
      this.medicationInstructions = '';
    } else if (mode === 'incident') {
      this.incidentDescription = '';
      this.incidentAction = '';
    } else if (mode === 'visit') {
      this.visitReason = '';
      this.visitObservations = '';
      this.visitAction = '';
    } else if (mode === 'vaccination') {
      this.vaccineName = '';
      this.vaccineDose = '';
    }
  }

  private today(): string {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;
    return new Date(now.getTime() - offset).toISOString().slice(0, 10);
  }
}
