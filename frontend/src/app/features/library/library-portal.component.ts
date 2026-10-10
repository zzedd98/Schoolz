import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  LibraryApiService,
  LibraryBorrower,
  LibraryPortalDashboard,
} from '../../core/library/library-api.service';

@Component({
  selector: 'app-library-portal',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './library-portal.component.html',
  styleUrl: './library-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LibraryPortalComponent {
  private readonly api = inject(LibraryApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly mode = this.route.snapshot.data['mode'] as 'student' | 'family';
  protected readonly data = signal<LibraryPortalDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected selectedStudentId = '';

  constructor() {
    this.load();
  }

  protected load(studentId?: string): void {
    this.loading.set(true);
    this.error.set('');
    const request =
      this.mode === 'student' ? this.api.studentPortal() : this.api.familyPortal(studentId);
    request
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.data.set(dashboard);
          this.selectedStudentId = dashboard.borrowers[0]?.student.id ?? '';
        },
        error: (response: { status?: number; error?: { detail?: string } }) => {
          if (this.mode === 'family' && response.status === 401) {
            void this.router.navigate(['/family']);
            return;
          }
          this.error.set(response.error?.detail ?? 'La bibliothèque ne peut pas être ouverte.');
        },
      });
  }

  protected borrower(): LibraryBorrower | null {
    const borrowers = this.data()?.borrowers ?? [];
    return (
      borrowers.find((item) => item.student.id === this.selectedStudentId) ?? borrowers[0] ?? null
    );
  }

  protected activeLoans(borrower: LibraryBorrower) {
    return borrower.loans.filter((loan) => loan.status === 'ACTIVE');
  }

  protected returnedLoans(borrower: LibraryBorrower) {
    return borrower.loans.filter((loan) => loan.status === 'RETURNED');
  }

  protected activeReservations(borrower: LibraryBorrower) {
    return borrower.reservations.filter((item) => ['READY', 'WAITING'].includes(item.status));
  }

  protected openFines(borrower: LibraryBorrower) {
    return borrower.fines.filter((fine) => fine.status === 'OPEN');
  }

  protected statusLabel(status: string): string {
    return (
      {
        ACTIVE: 'En cours',
        RETURNED: 'Rendu',
        READY: 'Prêt à retirer',
        WAITING: 'En attente',
        OPEN: 'À régulariser',
        PAID: 'Réglée',
        WAIVED: 'Levée',
      }[status] ?? status
    );
  }

  protected shortDate(value: string | null): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat('fr-MA', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(`${value.slice(0, 10)}T12:00:00`));
  }

  protected daysUntil(value: string): number {
    const today = new Date('2026-08-10T12:00:00').getTime();
    return Math.ceil((new Date(`${value}T12:00:00`).getTime() - today) / 86_400_000);
  }

  protected exit(): void {
    if (this.mode === 'family') {
      void this.router.navigate(['/family']);
      return;
    }
    this.auth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate(['/login']),
        error: () => void this.router.navigate(['/login']),
      });
  }
}
