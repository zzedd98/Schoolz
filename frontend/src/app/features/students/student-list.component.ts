import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';

import { LocaleService } from '../../core/i18n/locale.service';
import { StudentApiService, StudentSummary } from '../../core/students/student-api.service';

@Component({
  selector: 'app-student-list',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './student-list.component.html',
  styleUrl: './student-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentListComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(StudentApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly students = signal<readonly StudentSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly hasNext = signal(false);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);

  constructor() {
    this.load();
    this.search.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.load(value, 0));
  }

  protected initials(student: StudentSummary): string {
    return `${student.firstName[0] ?? ''}${student.lastName[0] ?? ''}`.toUpperCase();
  }

  protected load(search = this.search.value, page = this.page()): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .students(search, page)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.students.set(result.items);
          this.page.set(result.page);
          this.hasNext.set(result.hasNext);
        },
        error: () => this.failed.set(true),
      });
  }

  protected changePage(page: number): void {
    this.load(this.search.value, Math.max(0, page));
  }
}
