import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUser } from '../../core/auth/auth.models';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  const user = signal<CurrentUser>({
    id: 'admin-id',
    tenantId: 'tenant-id',
    email: 'admin@schoolz.ma',
    displayName: 'Admin SchoolZ',
    locale: 'fr-MA',
    roles: ['ADMIN'],
    permissions: ['global-search.read', 'communication.read', 'task.read', 'calendar.read'],
  });
  const auth = {
    user: user.asReadonly(),
    hasPermission: (permission: string) => user().permissions.includes(permission),
    logout: () => of(undefined),
  };

  beforeEach(async () => {
    (environment as { showTechnicalFeatures: boolean }).showTechnicalFeatures = false;
    user.set({
      ...user(),
      roles: ['ADMIN'],
      permissions: ['global-search.read', 'communication.read', 'task.read', 'calendar.read'],
    });
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('groups the administration navigation around essential daily actions', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('a[href="/app/dashboard"]')).not.toBeNull();
    expect(root.querySelector('a[href="/app/search"]')).not.toBeNull();
    expect(root.textContent).toContain('Essentiel');
    expect(root.querySelector('.skip-link')).not.toBeNull();
  });

  it('prioritizes the teacher day and mobile actions without exposing the admin overview', () => {
    user.set({
      ...user(),
      roles: ['TEACHER'],
      permissions: ['communication.read', 'task.read', 'calendar.read'],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('a[href="/app/teacher"]')).not.toBeNull();
    expect(root.querySelector('a[href="/app/dashboard"]')).toBeNull();
    expect(root.querySelector('.teacher-mobile-nav')).not.toBeNull();
  });

  it('keeps integration tools out of the production navigation', () => {
    user.set({
      ...user(),
      permissions: ['workflow.read', 'integration.read', 'public-api.read', 'webhook.read'],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('a[href="/app/workflows"]')).toBeNull();
    expect(root.querySelector('a[href="/app/integrations"]')).toBeNull();
    expect(root.querySelector('a[href="/app/api-access"]')).toBeNull();
    expect(root.querySelector('a[href="/app/webhooks"]')).toBeNull();
  });
});
