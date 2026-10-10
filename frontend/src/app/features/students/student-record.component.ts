import { KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { StudentApiService, StudentRecord } from '../../core/students/student-api.service';

type StudentSection =
  | 'identity'
  | 'family'
  | 'schooling'
  | 'attendance'
  | 'grades'
  | 'documents'
  | 'finance'
  | 'transport'
  | 'health'
  | 'history';

@Component({
  selector: 'app-student-record',
  imports: [KeyValuePipe, MatButtonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './student-record.component.html',
  styleUrl: './student-record.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentRecordComponent {
  protected readonly i18n = inject(LocaleService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(StudentApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly studentId = this.route.snapshot.paramMap.get('studentId') ?? '';

  protected readonly record = signal<StudentRecord | null>(null);
  protected readonly activeSection = signal<StudentSection>('identity');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly archiving = signal(false);
  protected readonly archivePanel = signal(false);
  protected readonly archiveReason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(300)],
  });

  protected readonly sections: readonly {
    id: StudentSection;
    key: Parameters<LocaleService['translate']>[0];
  }[] = [
    { id: 'identity', key: 'student.identity' },
    { id: 'family', key: 'student.family' },
    { id: 'schooling', key: 'student.schooling' },
    { id: 'attendance', key: 'student.attendance' },
    { id: 'grades', key: 'student.grades' },
    { id: 'documents', key: 'student.documents' },
    { id: 'finance', key: 'student.finance' },
    { id: 'transport', key: 'student.transport' },
    { id: 'health', key: 'student.health' },
    { id: 'history', key: 'student.history' },
  ];

  constructor() {
    this.load();
  }

  protected initials(record: StudentRecord): string {
    return `${record.identity.firstName[0] ?? ''}${record.identity.lastName[0] ?? ''}`.toUpperCase();
  }

  protected archive(): void {
    if (this.archiveReason.invalid) return;
    this.archiving.set(true);
    this.api
      .archive(this.studentId, this.archiveReason.value)
      .pipe(
        finalize(() => this.archiving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (record) => {
          this.record.set(record);
          this.archivePanel.set(false);
          this.activeSection.set('history');
        },
        error: () => this.failed.set(true),
      });
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .student(this.studentId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (record) => this.record.set(record),
        error: () => this.failed.set(true),
      });
  }
}
