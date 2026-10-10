import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin } from 'rxjs';

import { LocaleService } from '../../core/i18n/locale.service';
import {
  AcademicYearView,
  CampusView,
  ClassroomView,
  GradeLevelView,
  OrganizationApiService,
  PhysicalRoomView,
  SchoolLevelView,
} from '../../core/organization/organization-api.service';

type AcademicPanel = 'year' | 'term' | 'level' | 'grade' | 'offering' | 'room' | 'classroom' | null;

@Component({
  selector: 'app-academic-structure',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './academic-structure.component.html',
  styleUrl: './academic-structure.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcademicStructureComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(OrganizationApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly years = signal<readonly AcademicYearView[]>([]);
  protected readonly levels = signal<readonly SchoolLevelView[]>([]);
  protected readonly campuses = signal<readonly CampusView[]>([]);
  protected readonly rooms = signal<readonly PhysicalRoomView[]>([]);
  protected readonly classrooms = signal<readonly ClassroomView[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly saving = signal(false);
  protected readonly success = signal<string | null>(null);
  protected readonly panel = signal<AcademicPanel>(null);

  protected readonly yearForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startsOn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endsOn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly termForm = new FormGroup({
    yearId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    type: new FormControl<'TERM' | 'SEMESTER' | 'TRIMESTER'>('TRIMESTER', {
      nonNullable: true,
    }),
    sequence: new FormControl(1, { nonNullable: true, validators: [Validators.min(1)] }),
    startsOn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endsOn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly levelForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    sequence: new FormControl(1, { nonNullable: true, validators: [Validators.min(1)] }),
  });
  protected readonly gradeForm = new FormGroup({
    schoolLevelId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    sequence: new FormControl(1, { nonNullable: true, validators: [Validators.min(1)] }),
  });
  protected readonly offeringForm = new FormGroup({
    campusId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    gradeLevelIds: new FormControl<string[]>([], { nonNullable: true }),
  });
  protected readonly roomForm = new FormGroup({
    campusId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    roomType: new FormControl('CLASSROOM', { nonNullable: true }),
    capacity: new FormControl(25, { nonNullable: true, validators: [Validators.min(1)] }),
    floorLabel: new FormControl('', { nonNullable: true }),
  });
  protected readonly classroomForm = new FormGroup({
    campusId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    academicYearId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    gradeLevelId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    physicalRoomId: new FormControl<string | null>(null),
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    capacity: new FormControl(25, { nonNullable: true, validators: [Validators.min(1)] }),
  });

  constructor() {
    this.load();
  }

  protected allGrades(): readonly GradeLevelView[] {
    return this.levels().flatMap((level) => level.grades);
  }

  protected activeYear(): AcademicYearView | undefined {
    return this.years().find((year) => year.status === 'ACTIVE');
  }

  protected campusName(id: string): string {
    return this.campuses().find((campus) => campus.id === id)?.name ?? '—';
  }

  protected gradeName(id: string): string {
    return this.allGrades().find((grade) => grade.id === id)?.name ?? '—';
  }

  protected open(panel: Exclude<AcademicPanel, null>): void {
    this.failed.set(false);
    this.success.set(null);
    this.panel.set(panel);
  }

  protected close(): void {
    this.panel.set(null);
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      years: this.api.academicYears(),
      levels: this.api.schoolLevels(),
      campuses: this.api.campuses(),
      rooms: this.api.rooms(),
      classrooms: this.api.classrooms(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => {
          this.years.set(data.years);
          this.levels.set(data.levels);
          this.campuses.set(data.campuses);
          this.rooms.set(data.rooms);
          this.classrooms.set(data.classrooms);
        },
        error: () => this.failed.set(true),
      });
  }

  protected createYear(): void {
    if (this.yearForm.invalid) return;
    this.save(this.api.createAcademicYear(this.yearForm.getRawValue()), (year) => {
      this.years.update((items) => [year, ...items]);
    });
  }

  protected createTerm(): void {
    if (this.termForm.invalid) return;
    const { yearId, ...request } = this.termForm.getRawValue();
    this.save(this.api.createTerm(yearId, request), (term) => {
      this.years.update((items) =>
        items.map((year) =>
          year.id === yearId ? { ...year, terms: [...year.terms, term] } : year,
        ),
      );
    });
  }

  protected createLevel(): void {
    if (this.levelForm.invalid) return;
    this.save(this.api.createSchoolLevel(this.levelForm.getRawValue()), (level) => {
      this.levels.update((items) => [...items, level]);
    });
  }

  protected createGrade(): void {
    if (this.gradeForm.invalid) return;
    const { schoolLevelId, ...request } = this.gradeForm.getRawValue();
    this.save(this.api.createGradeLevel(schoolLevelId, request), (grade) => {
      this.levels.update((items) =>
        items.map((level) =>
          level.id === schoolLevelId ? { ...level, grades: [...level.grades, grade] } : level,
        ),
      );
    });
  }

  protected saveOfferings(): void {
    if (this.offeringForm.invalid) return;
    const value = this.offeringForm.getRawValue();
    this.save(this.api.setCampusOfferings(value.campusId, value.gradeLevelIds), (campus) => {
      this.campuses.update((items) => items.map((item) => (item.id === campus.id ? campus : item)));
    });
  }

  protected createRoom(): void {
    if (this.roomForm.invalid) return;
    this.save(this.api.createRoom(this.roomForm.getRawValue()), (room) => {
      this.rooms.update((items) => [...items, room]);
    });
  }

  protected createClassroom(): void {
    if (this.classroomForm.invalid) return;
    this.save(this.api.createClassroom(this.classroomForm.getRawValue()), (classroom) => {
      this.classrooms.update((items) => [...items, classroom]);
    });
  }

  protected activate(year: AcademicYearView): void {
    this.save(
      this.api.activateAcademicYear(year.id),
      (active) => {
        this.years.update((items) =>
          items.map((item) => ({
            ...item,
            status:
              item.id === active.id ? 'ACTIVE' : item.status === 'ACTIVE' ? 'CLOSED' : item.status,
          })),
        );
      },
      false,
    );
  }

  private save<T>(
    observable: import('rxjs').Observable<T>,
    apply: (value: T) => void,
    close = true,
  ): void {
    this.saving.set(true);
    this.failed.set(false);
    observable
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (value) => {
          apply(value);
          if (close) this.close();
          this.success.set(this.i18n.translate('academics.saved'));
        },
        error: () => this.failed.set(true),
      });
  }
}
