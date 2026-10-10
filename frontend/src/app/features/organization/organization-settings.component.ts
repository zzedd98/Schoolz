import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin } from 'rxjs';

import {
  CampusView,
  OrganizationApiService,
  OrganizationView,
  SchoolSettingsView,
} from '../../core/organization/organization-api.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-organization-settings',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './organization-settings.component.html',
  styleUrl: './organization-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationSettingsComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(OrganizationApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly organization = signal<OrganizationView | null>(null);
  protected readonly settings = signal<SchoolSettingsView | null>(null);
  protected readonly campuses = signal<readonly CampusView[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  protected readonly success = signal<string | null>(null);
  protected readonly showCampusForm = signal(false);

  protected readonly organizationForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    legalName: new FormControl('', { nonNullable: true }),
    addressLine1: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
    region: new FormControl('', { nonNullable: true }),
    postalCode: new FormControl('', { nonNullable: true }),
    countryCode: new FormControl('MA', { nonNullable: true, validators: [Validators.required] }),
    phone: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    ice: new FormControl('', { nonNullable: true }),
  });

  protected readonly settingsForm = new FormGroup({
    defaultLocale: new FormControl<'fr-MA' | 'ar-MA'>('fr-MA', { nonNullable: true }),
    currencyCode: new FormControl('MAD', { nonNullable: true, validators: [Validators.required] }),
    timezone: new FormControl('Africa/Casablanca', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    weekStartsOn: new FormControl(1, { nonNullable: true }),
    dayStartsAt: new FormControl('08:00', { nonNullable: true }),
    dayEndsAt: new FormControl('17:30', { nonNullable: true }),
    calendarNotes: new FormControl('', { nonNullable: true }),
  });

  protected readonly campusForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    addressLine1: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    region: new FormControl('Casablanca-Settat', { nonNullable: true }),
    postalCode: new FormControl('', { nonNullable: true }),
    countryCode: new FormControl('MA', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    locale: new FormControl<'fr-MA' | 'ar-MA'>('fr-MA', { nonNullable: true }),
    timezone: new FormControl('Africa/Casablanca', { nonNullable: true }),
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      organization: this.api.organization(),
      settings: this.api.settings(),
      campuses: this.api.campuses(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ organization, settings, campuses }) => {
          this.organization.set(organization);
          this.settings.set(settings);
          this.campuses.set(campuses);
          this.organizationForm.patchValue({
            code: organization.code,
            name: organization.name,
            legalName: organization.legalName ?? '',
            addressLine1: organization.addressLine1 ?? '',
            city: organization.city ?? '',
            region: organization.region ?? '',
            postalCode: organization.postalCode ?? '',
            countryCode: organization.countryCode,
            phone: organization.phone ?? '',
            email: organization.email ?? '',
            ice: organization.businessIdentifiers['ICE'] ?? '',
          });
          this.settingsForm.patchValue({
            defaultLocale: settings.defaultLocale,
            currencyCode: settings.currencyCode,
            timezone: settings.timezone,
            weekStartsOn: settings.weekStartsOn,
            dayStartsAt: settings.dayStartsAt.slice(0, 5),
            dayEndsAt: settings.dayEndsAt.slice(0, 5),
            calendarNotes: settings.calendarNotes ?? '',
          });
        },
        error: () => this.failed.set(true),
      });
  }

  protected saveOrganization(): void {
    if (this.organizationForm.invalid) return;
    const value = this.organizationForm.getRawValue();
    this.beginSave();
    this.api
      .configureOrganization({
        code: value.code,
        name: value.name,
        legalName: value.legalName,
        logoObjectKey: null,
        addressLine1: value.addressLine1,
        addressLine2: null,
        city: value.city,
        region: value.region,
        postalCode: value.postalCode,
        countryCode: value.countryCode,
        phone: value.phone,
        email: value.email,
        businessIdentifiers: value.ice ? { ICE: value.ice } : {},
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (organization) => {
          this.organization.set(organization);
          this.success.set(this.i18n.translate('organization.saved'));
        },
        error: () => this.failed.set(true),
      });
  }

  protected saveSettings(): void {
    if (this.settingsForm.invalid) return;
    const value = this.settingsForm.getRawValue();
    this.beginSave();
    this.api
      .configureSettings({
        defaultLocale: value.defaultLocale,
        supportedLocales: ['fr-MA', 'ar-MA'],
        currencyCode: value.currencyCode,
        timezone: value.timezone,
        weekStartsOn: value.weekStartsOn,
        workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
        dayStartsAt: `${value.dayStartsAt}:00`,
        dayEndsAt: `${value.dayEndsAt}:00`,
        calendarNotes: value.calendarNotes,
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => {
          this.settings.set(settings);
          this.success.set(this.i18n.translate('organization.saved'));
        },
        error: () => this.failed.set(true),
      });
  }

  protected createCampus(): void {
    if (this.campusForm.invalid) {
      this.campusForm.markAllAsTouched();
      return;
    }
    const value = this.campusForm.getRawValue();
    this.beginSave();
    this.api
      .createCampus({ ...value, addressLine2: null })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (campus) => {
          this.campuses.update((items) => [...items, campus]);
          this.showCampusForm.set(false);
          this.campusForm.reset({
            code: '',
            name: '',
            addressLine1: '',
            city: '',
            region: 'Casablanca-Settat',
            postalCode: '',
            countryCode: 'MA',
            phone: '',
            email: '',
            locale: 'fr-MA',
            timezone: 'Africa/Casablanca',
          });
          this.success.set(this.i18n.translate('organization.campusCreated'));
        },
        error: () => this.failed.set(true),
      });
  }

  private beginSave(): void {
    this.saving.set(true);
    this.failed.set(false);
    this.success.set(null);
  }
}
