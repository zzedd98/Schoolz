import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  StudentCourse,
  StudentDashboard,
  StudentGrade,
  StudentHomework,
  StudentPortalApiService,
} from '../../core/student-portal/student-portal-api.service';

type StudentSection = 'day' | 'grades' | 'homework' | 'courses' | 'assessments';

const SECTION_META: Record<
  StudentSection,
  { readonly eyebrow: string; readonly title: string; readonly description: string }
> = {
  day: {
    eyebrow: 'Emploi du temps',
    title: 'Ma journée',
    description: 'Tous tes cours de la journée, dans l’ordre, avec la salle et le professeur.',
  },
  grades: {
    eyebrow: 'Tes progrès',
    title: 'Toutes mes notes',
    description: 'L’historique de tes résultats publiés et les commentaires de tes professeurs.',
  },
  homework: {
    eyebrow: 'À ton rythme',
    title: 'Tout le travail à faire',
    description: 'Tes devoirs publiés, leurs échéances et ton suivi personnel.',
  },
  courses: {
    eyebrow: 'Bibliothèque personnelle',
    title: 'Tous mes cours',
    description: 'Les contenus et ressources publiés par tes professeurs.',
  },
  assessments: {
    eyebrow: 'À préparer',
    title: 'Tous les prochains DS',
    description: 'Les évaluations annoncées, leurs dates, matières et coefficients.',
  },
};

@Component({
  selector: 'app-student-section-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './student-section-page.component.html',
  styleUrl: './student-section-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StudentSectionPageComponent {
  private readonly api = inject(StudentPortalApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = this.route.snapshot.data['section'] as StudentSection;
  protected readonly meta = SECTION_META[this.section];
  protected readonly dashboard = signal<StudentDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly expandedCourse = signal<string | null>(null);
  protected selectedDate = this.route.snapshot.queryParamMap.get('date') ?? '';

  constructor() {
    this.load(this.selectedDate || undefined);
  }

  protected load(date?: string): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .all(date)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.selectedDate = dashboard.scheduleDate;
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ?? 'Impossible de charger cette page pour le moment.',
          ),
      });
  }

  protected selectDate(): void {
    if (this.selectedDate) this.load(this.selectedDate);
  }

  protected toggleHomework(homework: StudentHomework): void {
    const status = homework.status === 'DONE' ? 'TO_DO' : 'DONE';
    this.loading.set(true);
    this.error.set('');
    this.api
      .markHomework(homework.id, status)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          const current = this.dashboard();
          if (!current) return;
          this.dashboard.set({
            ...current,
            homework: current.homework.map((item) =>
              item.id === homework.id ? { ...item, status } : item,
            ),
          });
        },
        error: () => this.error.set("Le statut du devoir n'a pas pu être modifié."),
      });
  }

  protected toggleCourse(course: StudentCourse): void {
    this.expandedCourse.set(this.expandedCourse() === course.id ? null : course.id);
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

  protected count(data: StudentDashboard): number {
    switch (this.section) {
      case 'day':
        return data.lessons.length;
      case 'grades':
        return data.grades.length;
      case 'homework':
        return data.homework.length;
      case 'courses':
        return data.courses.length;
      case 'assessments':
        return data.assessments.length;
    }
  }

  protected initials(): string {
    const student = this.dashboard()?.student;
    return student ? `${student.firstName[0] ?? ''}${student.lastName[0] ?? ''}` : 'SZ';
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
      year: 'numeric',
    }).format(new Date(`${value}T12:00:00`));
  }

  protected dueDate(value: string): string {
    return new Intl.DateTimeFormat('fr-MA', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
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
}
