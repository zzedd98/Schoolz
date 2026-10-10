import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { TenantPreferenceService } from '../../core/auth/tenant-preference.service';
import { LocaleService, TranslationKey } from '../../core/i18n/locale.service';

interface NavigationItem {
  readonly path: string;
  readonly label: TranslationKey;
  readonly icon: string;
  readonly permission?: string;
  readonly onlyRole?: string;
  readonly excludeRole?: string;
  readonly technical?: boolean;
}

interface NavigationSection {
  readonly label: TranslationKey;
  readonly items: readonly NavigationItem[];
}

@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(LocaleService);
  protected readonly tenantSlug = inject(TenantPreferenceService).read();
  private readonly router = inject(Router);

  protected readonly menuOpen = signal(false);
  protected readonly navigation: readonly NavigationSection[] = [
    {
      label: 'shell.sectionEssential',
      items: [
        { path: '/app/teacher', label: 'shell.teacherPortal', icon: '⌂', onlyRole: 'TEACHER' },
        { path: '/app/dashboard', label: 'shell.overview', icon: '◫', excludeRole: 'TEACHER' },
        {
          path: '/app/search',
          label: 'shell.globalSearch',
          icon: '⌕',
          permission: 'global-search.read',
        },
        {
          path: '/app/notifications',
          label: 'shell.notifications',
          icon: '●',
          permission: 'communication.read',
        },
        { path: '/app/tasks', label: 'shell.tasks', icon: '✓', permission: 'task.read' },
        {
          path: '/app/calendar',
          label: 'shell.calendar',
          icon: '▦',
          permission: 'calendar.read',
        },
      ],
    },
    {
      label: 'shell.sectionSteering',
      items: [
        {
          path: '/app/analytics',
          label: 'shell.analytics',
          icon: '⌁',
          permission: 'analytics.read',
        },
        {
          path: '/app/early-warning',
          label: 'shell.earlyWarning',
          icon: '◎',
          permission: 'early-warning.read',
        },
        {
          path: '/app/workflows',
          label: 'shell.workflows',
          icon: '↝',
          permission: 'workflow.read',
          technical: true,
        },
      ],
    },
    {
      label: 'shell.sectionSchool',
      items: [
        {
          path: '/app/organization',
          label: 'shell.organization',
          icon: '⌘',
          permission: 'organization.read',
        },
        {
          path: '/app/academics',
          label: 'shell.academic',
          icon: '▦',
          permission: 'academic-structure.read',
        },
        {
          path: '/app/students',
          label: 'shell.students',
          icon: '◎',
          permission: 'student.read',
        },
        {
          path: '/app/imports',
          label: 'shell.imports',
          icon: '⇩',
          permission: 'data-import.read',
        },
        {
          path: '/app/admissions',
          label: 'shell.admissions',
          icon: '◇',
          permission: 'admissions.read',
        },
        {
          path: '/app/enrollments',
          label: 'shell.enrollments',
          icon: '✓',
          permission: 'enrollment.read',
        },
        {
          path: '/app/childcare',
          label: 'shell.childcare',
          icon: '☀',
          permission: 'childcare.read',
        },
        {
          path: '/app/attendance',
          label: 'shell.attendance',
          icon: '✓',
          permission: 'attendance.read',
        },
        {
          path: '/app/timetable',
          label: 'shell.timetable',
          icon: '▦',
          permission: 'timetable.read',
        },
        { path: '/app/grading', label: 'shell.grading', icon: 'A+', permission: 'grade.read' },
        {
          path: '/app/homework',
          label: 'shell.homework',
          icon: '▤',
          permission: 'homework.read',
        },
      ],
    },
    {
      label: 'shell.sectionRelations',
      items: [
        {
          path: '/app/finance',
          label: 'shell.finance',
          icon: '◇',
          permission: 'invoice.read',
        },
        {
          path: '/app/communications',
          label: 'shell.communications',
          icon: '✉',
          permission: 'communication.read',
        },
        {
          path: '/app/documents',
          label: 'shell.documents',
          icon: '▤',
          permission: 'document.read',
        },
        {
          path: '/app/signatures',
          label: 'shell.signatures',
          icon: '✓',
          permission: 'document.sign',
        },
        { path: '/family', label: 'shell.family', icon: '⌂' },
      ],
    },
    {
      label: 'shell.sectionServices',
      items: [
        {
          path: '/app/transport',
          label: 'shell.transport',
          icon: '▰',
          permission: 'transport.read',
        },
        {
          path: '/app/cafeteria',
          label: 'shell.cafeteria',
          icon: '◉',
          permission: 'cafeteria.read',
        },
        {
          path: '/app/activities',
          label: 'shell.activities',
          icon: '★',
          permission: 'activity.read',
        },
        {
          path: '/app/library',
          label: 'shell.library',
          icon: '▤',
          permission: 'library.read',
        },
        {
          path: '/app/health',
          label: 'shell.health',
          icon: '✚',
          permission: 'health.read',
        },
      ],
    },
    {
      label: 'shell.sectionManagement',
      items: [
        { path: '/app/hr', label: 'shell.hr', icon: '♙', permission: 'hr.read' },
        {
          path: '/app/payroll',
          label: 'shell.payroll',
          icon: '◇',
          permission: 'payroll.read',
        },
        {
          path: '/app/accounting',
          label: 'shell.accounting',
          icon: '≋',
          permission: 'accounting.read',
        },
        {
          path: '/app/procurement',
          label: 'shell.procurement',
          icon: '↝',
          permission: 'procurement.read',
        },
        {
          path: '/app/inventory',
          label: 'shell.inventory',
          icon: '◫',
          permission: 'inventory.read',
        },
      ],
    },
    {
      label: 'shell.sectionPlatform',
      items: [
        { path: '/app/users', label: 'shell.users', icon: '◎', permission: 'user.read' },
        {
          path: '/app/configuration',
          label: 'shell.businessConfiguration',
          icon: '⌘',
          permission: 'business-configuration.read',
        },
        {
          path: '/app/custom-fields',
          label: 'shell.customFields',
          icon: '◇',
          permission: 'custom-field.read',
        },
        {
          path: '/app/integrations',
          label: 'shell.integrations',
          icon: '⇄',
          permission: 'integration.read',
          technical: true,
        },
        {
          path: '/app/api-access',
          label: 'shell.publicApi',
          icon: '{}',
          permission: 'public-api.read',
          technical: true,
        },
        {
          path: '/app/webhooks',
          label: 'shell.webhooks',
          icon: '↝',
          permission: 'webhook.read',
          technical: true,
        },
        {
          path: '/app/privacy',
          label: 'shell.privacy',
          icon: '◉',
          permission: 'privacy.read',
        },
      ],
    },
  ];

  protected isTeacher(): boolean {
    return this.auth.user()?.roles.includes('TEACHER') ?? false;
  }

  protected isVisible(item: NavigationItem): boolean {
    const roles = this.auth.user()?.roles ?? [];
    if (item.technical && !environment.showTechnicalFeatures) return false;
    if (item.onlyRole && !roles.includes(item.onlyRole)) return false;
    if (item.excludeRole && roles.includes(item.excludeRole)) return false;
    return !item.permission || this.auth.hasPermission(item.permission);
  }

  protected hasVisibleItem(section: NavigationSection): boolean {
    return section.items.some((item) => this.isVisible(item));
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }
}
