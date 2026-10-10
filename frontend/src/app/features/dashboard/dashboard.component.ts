import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AdminApiService } from '../../core/admin/admin-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { LocaleService } from '../../core/i18n/locale.service';

@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(LocaleService);
  private readonly adminApi = inject(AdminApiService);

  protected readonly userCount = signal<number | null>(null);
  protected readonly roleCount = signal<number | null>(null);

  constructor() {
    if (this.auth.hasPermission('user.read')) {
      forkJoin({
        users: this.adminApi.users(),
        roles: this.adminApi.roles(),
      }).subscribe({
        next: ({ users, roles }) => {
          this.userCount.set(users.length);
          this.roleCount.set(roles.length);
        },
        error: () => {
          this.userCount.set(0);
          this.roleCount.set(0);
        },
      });
    }
  }

  protected roleLabel(role: string): string {
    const labels: Record<string, string> = {
      ADMIN: 'Direction',
      SUPER_ADMIN: 'Direction générale',
      ORGANIZATION_OWNER: "Responsable d'établissement",
      TEACHER: 'Enseignant',
      EDUCATOR: 'Équipe éducative',
      PARENT: 'Parent',
      STUDENT: 'Élève',
    };
    return labels[role] ?? role.toLowerCase().replaceAll('_', ' ');
  }
}
