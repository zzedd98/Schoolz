import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

import {
  LibraryApiService,
  LibraryBook,
  LibraryCopy,
  LibraryDashboard,
  LibraryFine,
  LibraryLoan,
  LibraryReservation,
} from '../../core/library/library-api.service';

type LibrarySection = 'catalog' | 'loans' | 'reservations' | 'rules';
type LibraryDrawer =
  'book' | 'copy' | 'loan' | 'return' | 'reservation' | 'author' | 'category' | 'fine' | null;

@Component({
  selector: 'app-library-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './library-dashboard.component.html',
  styleUrl: './library-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LibraryDashboardComponent {
  private readonly api = inject(LibraryApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly data = signal<LibraryDashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeSection = signal<LibrarySection>('catalog');
  protected readonly drawer = signal<LibraryDrawer>(null);
  protected readonly selectedBook = signal<LibraryBook | null>(null);
  protected readonly selectedLoan = signal<LibraryLoan | null>(null);
  protected readonly selectedFine = signal<LibraryFine | null>(null);
  protected readonly scanResult = signal<LibraryCopy | null>(null);

  protected scanCode = '';
  protected bookForm = this.emptyBook();
  protected copyForm = this.emptyCopy();
  protected loanForm = this.emptyLoan();
  protected returnForm = { returnedOn: '2026-08-10', conditionIn: 'GOOD', note: '' };
  protected reservationForm = { bookId: '', studentId: '' };
  protected authorForm = { displayName: '', sortName: '', biography: '' };
  protected categoryForm = { code: '', name: '', color: '#3b82f6' };
  protected fineForm = { resolution: 'PAID', note: '' };
  protected configurationForm = {
    fineEnabled: true,
    finePerOverdueDay: 2,
    maximumFine: 100,
    defaultLoanDays: 14,
    reservationHoldDays: 3,
  };

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .dashboard()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.data.set(dashboard);
          this.configurationForm = { ...dashboard.configuration };
        },
        error: () => this.error.set('La bibliothèque ne peut pas être chargée pour le moment.'),
      });
  }

  protected selectSection(section: LibrarySection): void {
    this.activeSection.set(section);
  }

  protected openBook(): void {
    const data = this.data();
    this.bookForm = { ...this.emptyBook(), categoryId: data?.categories[0]?.id ?? '' };
    this.open('book');
  }

  protected openCopy(book: LibraryBook): void {
    this.selectedBook.set(book);
    const locationId = this.data()?.copies.find((copy) => copy.locationId)?.locationId ?? '';
    this.copyForm = { ...this.emptyCopy(), locationId: locationId ?? '' };
    this.open('copy');
  }

  protected openLoan(copy?: LibraryCopy): void {
    const available = copy ?? this.data()?.copies.find((item) => item.status === 'AVAILABLE');
    this.loanForm = {
      ...this.emptyLoan(),
      copyId: available?.id ?? '',
      studentId: this.data()?.students[0]?.id ?? '',
    };
    this.open('loan');
  }

  protected openReturn(loan: LibraryLoan): void {
    this.selectedLoan.set(loan);
    this.returnForm = { returnedOn: '2026-08-10', conditionIn: 'GOOD', note: '' };
    this.open('return');
  }

  protected openReservation(): void {
    this.reservationForm = {
      bookId: this.data()?.books[0]?.id ?? '',
      studentId: this.data()?.students[0]?.id ?? '',
    };
    this.open('reservation');
  }

  protected openFine(fine: LibraryFine): void {
    this.selectedFine.set(fine);
    this.fineForm = { resolution: 'PAID', note: '' };
    this.open('fine');
  }

  protected open(drawer: LibraryDrawer): void {
    this.notice.set('');
    this.error.set('');
    this.drawer.set(drawer);
  }

  protected closeDrawer(): void {
    this.drawer.set(null);
    this.selectedBook.set(null);
    this.selectedLoan.set(null);
    this.selectedFine.set(null);
  }

  protected scan(): void {
    const code = this.scanCode.trim();
    if (!code) return;
    this.loading.set(true);
    this.error.set('');
    this.scanResult.set(null);
    this.api
      .scan(code)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (copy) => this.scanResult.set(copy),
        error: () => this.error.set('Aucun exemplaire ne correspond à ce code.'),
      });
  }

  protected saveBook(): void {
    this.mutate(
      this.api.createBook({
        ...this.bookForm,
        isbn: this.bookForm.isbn || null,
        subtitle: this.bookForm.subtitle || null,
        publisher: this.bookForm.publisher || null,
        publicationYear: this.bookForm.publicationYear || null,
        description: this.bookForm.description || null,
        authorIds: this.bookForm.authorId ? [this.bookForm.authorId] : [],
      }),
      'Le titre rejoint le catalogue. Ajoutez maintenant ses exemplaires.',
    );
  }

  protected saveCopy(): void {
    const book = this.selectedBook();
    if (!book) return;
    this.mutate(
      this.api.createCopy(book.id, {
        ...this.copyForm,
        locationId: this.copyForm.locationId || null,
        barcode: this.copyForm.barcode || null,
        qrCode: this.copyForm.qrCode || null,
        acquiredOn: this.copyForm.acquiredOn || null,
        notes: this.copyForm.notes || null,
      }),
      'L’exemplaire est équipé et prêt à être scanné.',
    );
  }

  protected saveLoan(): void {
    this.mutate(
      this.api.createLoan({ ...this.loanForm, note: this.loanForm.note || null }),
      'Le prêt est ouvert et visible dans le portail de l’élève.',
    );
  }

  protected saveReturn(): void {
    const loan = this.selectedLoan();
    if (!loan) return;
    this.mutate(
      this.api.returnLoan(loan.id, { ...this.returnForm, note: this.returnForm.note || null }),
      'Le retour est enregistré ; la file de réservation a été réévaluée.',
    );
  }

  protected saveReservation(): void {
    this.mutate(
      this.api.createReservation(this.reservationForm),
      'La réservation est enregistrée avec sa position réelle dans la file.',
    );
  }

  protected cancelReservation(reservation: LibraryReservation): void {
    this.mutate(
      this.api.cancelReservation(reservation.id),
      'La réservation est annulée et le prochain lecteur a été prévenu.',
      false,
    );
  }

  protected saveAuthor(): void {
    this.mutate(
      this.api.createAuthor({
        ...this.authorForm,
        sortName: this.authorForm.sortName || this.authorForm.displayName.toUpperCase(),
        biography: this.authorForm.biography || null,
      }),
      'L’auteur est disponible dans le catalogue.',
    );
  }

  protected saveCategory(): void {
    this.mutate(this.api.createCategory(this.categoryForm), 'La collection est créée.');
  }

  protected saveConfiguration(): void {
    this.mutate(
      this.api.configure(this.configurationForm),
      'Les règles de circulation sont enregistrées.',
      false,
    );
  }

  protected resolveFine(): void {
    const fine = this.selectedFine();
    if (!fine) return;
    this.mutate(
      this.api.resolveFine(fine.id, this.fineForm),
      this.fineForm.resolution === 'PAID'
        ? 'Le règlement est enregistré.'
        : 'La pénalité est levée avec sa justification.',
    );
  }

  protected activeLoans(): readonly LibraryLoan[] {
    return this.data()?.loans.filter((loan) => loan.status === 'ACTIVE') ?? [];
  }

  protected historyLoans(): readonly LibraryLoan[] {
    return this.data()?.loans.filter((loan) => loan.status !== 'ACTIVE') ?? [];
  }

  protected activeReservations(): readonly LibraryReservation[] {
    return (
      this.data()?.reservations.filter((item) => ['READY', 'WAITING'].includes(item.status)) ?? []
    );
  }

  protected openFines(): readonly LibraryFine[] {
    return this.data()?.fines.filter((fine) => fine.status === 'OPEN') ?? [];
  }

  protected locations(): readonly { id: string; name: string }[] {
    const byId = new Map<string, string>();
    for (const copy of this.data()?.copies ?? []) {
      if (copy.locationId)
        byId.set(copy.locationId, copy.locationName ?? copy.locationCode ?? 'Lieu');
    }
    return [...byId].map(([id, name]) => ({ id, name }));
  }

  protected statusLabel(status: string): string {
    return (
      {
        AVAILABLE: 'Disponible',
        RESERVED: 'Réservé',
        ON_LOAN: 'Emprunté',
        LOST: 'Perdu',
        DAMAGED: 'Endommagé',
        ACTIVE: 'En cours',
        RETURNED: 'Rendu',
        READY: 'À retirer',
        WAITING: 'En attente',
        CANCELLED: 'Annulée',
        OPEN: 'À traiter',
        PAID: 'Réglée',
        WAIVED: 'Levée',
      }[status] ?? status
    );
  }

  protected shortDate(value: string | null): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat('fr-MA', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(new Date(`${value.slice(0, 10)}T12:00:00`));
  }

  private mutate(request: Observable<unknown>, success: string, closeDrawer = true): void {
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    request
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notice.set(success);
          if (closeDrawer) this.closeDrawer();
          this.load();
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(response.error?.detail ?? 'Cette opération ne peut pas être enregistrée.'),
      });
  }

  private emptyBook() {
    return {
      categoryId: '',
      isbn: '',
      title: '',
      subtitle: '',
      publisher: '',
      publicationYear: 2026,
      languageCode: 'fr',
      description: '',
      authorId: '',
    };
  }

  private emptyCopy() {
    return {
      locationId: '',
      accessionNumber: '',
      barcode: '',
      qrCode: '',
      acquiredOn: '2026-08-10',
      condition: 'GOOD',
      notes: '',
    };
  }

  private emptyLoan() {
    return {
      copyId: '',
      studentId: '',
      loanedOn: '2026-08-10',
      dueOn: '2026-08-24',
      conditionOut: 'GOOD',
      note: '',
    };
  }
}
