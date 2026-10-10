import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import {
  StudentDashboard,
  StudentGrade,
  StudentHomework,
  StudentPortalApiService,
} from '../../core/student-portal/student-portal-api.service';

@Component({
  selector: 'app-student-portal',
  imports: [FormsModule, RouterLink],
  templateUrl: './student-portal.component.html',
  styleUrl: './student-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentPortalComponent {
  private readonly api = inject(StudentPortalApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dashboard = signal<StudentDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected selectedDate = '';

  constructor() {
    this.load();
  }

  protected load(date?: string): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .dashboard(date)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.selectedDate = dashboard.requestedDate;
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ?? 'Impossible de charger ton espace pour le moment.',
          ),
      });
  }

  protected selectDate(): void {
    if (this.selectedDate) this.load(this.selectedDate);
  }

  protected toggleHomework(homework: StudentHomework): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .markHomework(homework.id, homework.status === 'DONE' ? 'TO_DO' : 'DONE')
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.selectedDate = dashboard.requestedDate;
        },
        error: () => this.error.set("Le statut du devoir n'a pas pu être modifié."),
      });
  }

  protected logout(): void {
    this.auth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate(['/login']),
        error: () => void this.router.navigate(['/login']),
      });
  }

  protected shortDate(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', { day: '2-digit', month: 'short' }).format(
      new Date(`${value}T12:00:00`),
    );
  }

  protected longDate(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(new Date(`${value}T12:00:00`));
  }

  protected dueDate(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }

  protected time(value: string): string {
    return value.slice(0, 5);
  }

  protected gradeOn20(grade: StudentGrade): string {
    if (grade.score == null || grade.maximumScore == null) return '—';
    const normalized = (grade.score * 20) / grade.maximumScore;
    return Number.isInteger(normalized) ? `${normalized}` : normalized.toFixed(1);
  }

  protected gradePercent(grade: StudentGrade): number {
    if (grade.score == null || grade.maximumScore == null) return 0;
    return Math.min(100, Math.max(0, (grade.score / grade.maximumScore) * 100));
  }

  protected initials(): string {
    const student = this.dashboard()?.student;
    return student ? `${student.firstName[0] ?? ''}${student.lastName[0] ?? ''}` : 'SZ';
  }
}
