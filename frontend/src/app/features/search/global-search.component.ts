import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import {
  GlobalSearchApiService,
  GlobalSearchResponse,
  GlobalSearchType,
} from '../../core/search/global-search-api.service';

@Component({
  selector: 'app-global-search',
  imports: [FormsModule, RouterLink],
  templateUrl: './global-search.component.html',
  styleUrl: './global-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalSearchComponent {
  private readonly api = inject(GlobalSearchApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly data = signal<GlobalSearchResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected query = '';
  protected activeType = '';

  protected readonly labels: Readonly<Record<GlobalSearchType, string>> = {
    STUDENT: 'Élèves',
    PARENT: 'Parents',
    TEACHER: 'Enseignants',
    INVOICE: 'Factures',
    CLASSROOM: 'Classes',
    DOCUMENT: 'Documents',
  };

  protected readonly icons: Readonly<Record<GlobalSearchType, string>> = {
    STUDENT: 'ÉL',
    PARENT: 'PA',
    TEACHER: 'PR',
    INVOICE: 'DH',
    CLASSROOM: 'CL',
    DOCUMENT: 'DO',
  };

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      this.query = params.get('q') ?? '';
      this.activeType = params.get('type') ?? '';
      if (this.query.trim().length >= 2) this.load(Number(params.get('page') ?? 0));
    });
  }

  protected submit(): void {
    const query = this.query.trim();
    if (query.length < 2) {
      this.error.set('Saisissez au moins deux caractères.');
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: query, type: this.activeType || null, page: null },
    });
  }

  protected selectType(type: string): void {
    this.activeType = type;
    if (this.query.trim().length >= 2) this.submit();
  }

  protected go(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: this.query.trim(), type: this.activeType || null, page },
    });
  }

  protected refresh(): void {
    this.refreshing.set(true);
    this.message.set('');
    this.error.set('');
    this.api.refresh().subscribe({
      next: (result) => {
        this.refreshing.set(false);
        this.message.set(`${result.documentCount} fiches réindexées dans PostgreSQL.`);
        if (this.query.trim().length >= 2) this.load(0);
      },
      error: (response) => {
        this.refreshing.set(false);
        this.error.set(response?.error?.detail ?? 'L’index ne peut pas être actualisé.');
      },
    });
  }

  private load(page: number): void {
    this.loading.set(true);
    this.error.set('');
    this.api.search(this.query.trim(), this.activeType, page).subscribe({
      next: (result) => {
        this.data.set(result);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(response?.error?.detail ?? 'La recherche ne peut pas être effectuée.');
      },
    });
  }
}
