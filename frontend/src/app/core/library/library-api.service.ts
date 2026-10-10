import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface LibraryConfiguration {
  readonly fineEnabled: boolean;
  readonly finePerOverdueDay: number;
  readonly maximumFine: number;
  readonly defaultLoanDays: number;
  readonly reservationHoldDays: number;
}

export interface LibraryAuthor {
  readonly id: string;
  readonly displayName: string;
  readonly sortName: string;
  readonly biography: string | null;
}

export interface LibraryCategory {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly color: string;
  readonly active: boolean;
}

export interface LibraryBookAuthor {
  readonly bookId: string;
  readonly authorId: string;
  readonly authorName: string;
  readonly position: number;
}

export interface LibraryBook {
  readonly id: string;
  readonly categoryId: string;
  readonly categoryCode: string;
  readonly categoryName: string;
  readonly categoryColor: string;
  readonly isbn: string | null;
  readonly title: string;
  readonly subtitle: string | null;
  readonly publisher: string | null;
  readonly publicationYear: number | null;
  readonly languageCode: string;
  readonly description: string | null;
  readonly coverKey: string | null;
  readonly active: boolean;
  readonly copyCount: number;
  readonly availableCount: number;
  readonly authors: readonly LibraryBookAuthor[];
}

export interface LibraryCopy {
  readonly id: string;
  readonly bookId: string;
  readonly bookTitle: string;
  readonly locationId: string | null;
  readonly locationCode: string | null;
  readonly locationName: string | null;
  readonly accessionNumber: string;
  readonly barcode: string | null;
  readonly qrCode: string | null;
  readonly acquiredOn: string | null;
  readonly condition: string;
  readonly status: string;
  readonly notes: string | null;
}

export interface LibraryLoan {
  readonly id: string;
  readonly copyId: string;
  readonly bookId: string;
  readonly accessionNumber: string;
  readonly barcode: string | null;
  readonly bookTitle: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly reservationId: string | null;
  readonly loanedOn: string;
  readonly dueOn: string;
  readonly returnedOn: string | null;
  readonly conditionOut: string;
  readonly conditionIn: string | null;
  readonly status: string;
  readonly note: string | null;
  readonly overdue: boolean;
  readonly fineId: string | null;
  readonly fineAmount: number | null;
  readonly fineStatus: string | null;
}

export interface LibraryReservation {
  readonly id: string;
  readonly bookId: string;
  readonly bookTitle: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly readyCopyId: string | null;
  readonly accessionNumber: string | null;
  readonly queuePosition: number;
  readonly status: string;
  readonly reservedAt: string;
  readonly readyAt: string | null;
  readonly expiresAt: string | null;
  readonly fulfilledAt: string | null;
  readonly cancelledAt: string | null;
}

export interface LibraryFine {
  readonly id: string;
  readonly loanId: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly bookTitle: string;
  readonly overdueDays: number;
  readonly amount: number;
  readonly currency: string;
  readonly status: string;
  readonly resolutionNote: string | null;
  readonly assessedAt: string;
  readonly resolvedAt: string | null;
}

export interface LibraryStudent {
  readonly id: string;
  readonly displayName: string;
  readonly studentNumber: string | null;
}

export interface LibraryDashboard {
  readonly generatedAt: string;
  readonly configuration: LibraryConfiguration;
  readonly statistics: {
    readonly books: number;
    readonly copies: number;
    readonly availableCopies: number;
    readonly activeLoans: number;
    readonly overdueLoans: number;
    readonly activeReservations: number;
    readonly openFines: number;
  };
  readonly authors: readonly LibraryAuthor[];
  readonly categories: readonly LibraryCategory[];
  readonly books: readonly LibraryBook[];
  readonly copies: readonly LibraryCopy[];
  readonly loans: readonly LibraryLoan[];
  readonly reservations: readonly LibraryReservation[];
  readonly fines: readonly LibraryFine[];
  readonly students: readonly LibraryStudent[];
}

export interface LibraryBorrower {
  readonly student: LibraryStudent;
  readonly loans: readonly LibraryLoan[];
  readonly reservations: readonly LibraryReservation[];
  readonly fines: readonly LibraryFine[];
}

export interface LibraryPortalDashboard {
  readonly generatedAt: string;
  readonly configuration: LibraryConfiguration;
  readonly borrowers: readonly LibraryBorrower[];
}

@Injectable({ providedIn: 'root' })
export class LibraryApiService {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<LibraryDashboard>('/api/v1/library/dashboard');
  }

  configure(command: object) {
    return this.http.put<LibraryConfiguration>('/api/v1/library/configuration', command);
  }

  createAuthor(command: object) {
    return this.http.post<LibraryAuthor>('/api/v1/library/authors', command);
  }

  createCategory(command: object) {
    return this.http.post<LibraryCategory>('/api/v1/library/categories', command);
  }

  createBook(command: object) {
    return this.http.post<LibraryBook>('/api/v1/library/books', command);
  }

  createCopy(bookId: string, command: object) {
    return this.http.post<LibraryCopy>(`/api/v1/library/books/${bookId}/copies`, command);
  }

  scan(code: string) {
    return this.http.get<LibraryCopy>('/api/v1/library/copies/scan', {
      params: new HttpParams().set('code', code),
    });
  }

  createLoan(command: object) {
    return this.http.post<LibraryLoan>('/api/v1/library/loans', command);
  }

  returnLoan(loanId: string, command: object) {
    return this.http.post<LibraryLoan>(`/api/v1/library/loans/${loanId}/return`, command);
  }

  createReservation(command: object) {
    return this.http.post<LibraryReservation>('/api/v1/library/reservations', command);
  }

  cancelReservation(reservationId: string) {
    return this.http.post<LibraryReservation>(
      `/api/v1/library/reservations/${reservationId}/cancel`,
      {},
    );
  }

  resolveFine(fineId: string, command: object) {
    return this.http.post<LibraryFine>(`/api/v1/library/fines/${fineId}/resolve`, command);
  }

  studentPortal() {
    return this.http.get<LibraryPortalDashboard>('/api/v1/student-portal/library');
  }

  familyPortal(studentId?: string) {
    let params = new HttpParams();
    if (studentId) params = params.set('studentId', studentId);
    return this.http.get<LibraryPortalDashboard>('/api/v1/public/library', {
      params,
      withCredentials: true,
    });
  }
}
