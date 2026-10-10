import { Routes } from '@angular/router';

import { familySessionGuard, studentGuard, workspaceGuard } from './core/auth/auth.guard';
import { technicalFeatureGuard } from './core/feature-visibility/technical-feature.guard';

export const routes: Routes = [
  {
    path: 'student/library',
    canActivate: [studentGuard],
    data: { mode: 'student' },
    loadComponent: () =>
      import('./features/library/library-portal.component').then(
        (component) => component.LibraryPortalComponent,
      ),
  },
  ...(['day', 'grades', 'homework', 'courses', 'assessments'] as const).map((section) => ({
    path: `student/${section}`,
    canActivate: [studentGuard],
    data: { section },
    loadComponent: () =>
      import('./features/student-portal/student-section-page.component').then(
        (component) => component.StudentSectionPageComponent,
      ),
  })),
  {
    path: 'student',
    canActivate: [studentGuard],
    loadComponent: () =>
      import('./features/student-portal/student-portal.component').then(
        (component) => component.StudentPortalComponent,
      ),
  },
  {
    path: 'family',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/family-portal/family-portal.component').then(
        (component) => component.FamilyPortalComponent,
      ),
  },
  {
    path: 'library',
    canActivate: [familySessionGuard],
    data: { mode: 'family' },
    loadComponent: () =>
      import('./features/library/library-portal.component').then(
        (component) => component.LibraryPortalComponent,
      ),
  },
  {
    path: 'finance',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/finance/finance-portal.component').then(
        (component) => component.FinancePortalComponent,
      ),
  },
  {
    path: 'messages',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/communications/communications-portal.component').then(
        (component) => component.CommunicationsPortalComponent,
      ),
  },
  {
    path: 'documents',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/documents/documents-portal.component').then(
        (component) => component.DocumentsPortalComponent,
      ),
  },
  {
    path: 'signatures',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/documents/document-signatures-portal.component').then(
        (component) => component.DocumentSignaturesPortalComponent,
      ),
  },
  {
    path: 'transport',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/transport/transport-portal.component').then(
        (component) => component.TransportPortalComponent,
      ),
  },
  {
    path: 'cafeteria',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/services/school-services-portal.component').then(
        (component) => component.SchoolServicesPortalComponent,
      ),
  },
  {
    path: 'activities',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/services/school-services-portal.component').then(
        (component) => component.SchoolServicesPortalComponent,
      ),
  },
  {
    path: 'admissions',
    loadComponent: () =>
      import('./features/admissions/admission-portal.component').then(
        (component) => component.AdmissionPortalComponent,
      ),
  },
  {
    path: 'enrollment',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/enrollments/enrollment-portal.component').then(
        (component) => component.EnrollmentPortalComponent,
      ),
  },
  {
    path: 'childcare',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/childcare/childcare-report.component').then(
        (component) => component.ChildcareReportComponent,
      ),
  },
  {
    path: 'attendance',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/attendance/attendance-portal.component').then(
        (component) => component.AttendancePortalComponent,
      ),
  },
  {
    path: 'homework',
    canActivate: [familySessionGuard],
    loadComponent: () =>
      import('./features/homework/homework-portal.component').then(
        (component) => component.HomeworkPortalComponent,
      ),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((component) => component.LoginComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/password-recovery.component').then(
        (component) => component.PasswordRecoveryComponent,
      ),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/password-recovery.component').then(
        (component) => component.PasswordRecoveryComponent,
      ),
  },
  {
    path: 'activate-account',
    loadComponent: () =>
      import('./features/auth/password-recovery.component').then(
        (component) => component.PasswordRecoveryComponent,
      ),
  },
  {
    path: 'app',
    canActivate: [workspaceGuard],
    loadComponent: () =>
      import('./features/shell/shell.component').then((component) => component.ShellComponent),
    children: [
      {
        path: 'teacher',
        loadComponent: () =>
          import('./features/teacher-portal/teacher-portal.component').then(
            (component) => component.TeacherPortalComponent,
          ),
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(
            (component) => component.DashboardComponent,
          ),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/users/users.component').then((component) => component.UsersComponent),
      },
      {
        path: 'organization',
        loadComponent: () =>
          import('./features/organization/organization-settings.component').then(
            (component) => component.OrganizationSettingsComponent,
          ),
      },
      {
        path: 'academics',
        loadComponent: () =>
          import('./features/academics/academic-structure.component').then(
            (component) => component.AcademicStructureComponent,
          ),
      },
      {
        path: 'students',
        loadComponent: () =>
          import('./features/students/student-list.component').then(
            (component) => component.StudentListComponent,
          ),
      },
      {
        path: 'students/:studentId',
        loadComponent: () =>
          import('./features/students/student-record.component').then(
            (component) => component.StudentRecordComponent,
          ),
      },
      {
        path: 'admissions',
        loadComponent: () =>
          import('./features/admissions/admission-crm.component').then(
            (component) => component.AdmissionCrmComponent,
          ),
      },
      {
        path: 'enrollments',
        loadComponent: () =>
          import('./features/enrollments/enrollment-admin.component').then(
            (component) => component.EnrollmentAdminComponent,
          ),
      },
      {
        path: 'childcare',
        loadComponent: () =>
          import('./features/childcare/childcare-dashboard.component').then(
            (component) => component.ChildcareDashboardComponent,
          ),
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./features/attendance/attendance-dashboard.component').then(
            (component) => component.AttendanceDashboardComponent,
          ),
      },
      {
        path: 'timetable',
        loadComponent: () =>
          import('./features/timetable/timetable-dashboard.component').then(
            (component) => component.TimetableDashboardComponent,
          ),
      },
      {
        path: 'grading',
        loadComponent: () =>
          import('./features/grading/grading-dashboard.component').then(
            (component) => component.GradingDashboardComponent,
          ),
      },
      {
        path: 'homework',
        loadComponent: () =>
          import('./features/homework/homework-dashboard.component').then(
            (component) => component.HomeworkDashboardComponent,
          ),
      },
      {
        path: 'finance',
        loadComponent: () =>
          import('./features/finance/finance-dashboard.component').then(
            (component) => component.FinanceDashboardComponent,
          ),
      },
      {
        path: 'communications',
        loadComponent: () =>
          import('./features/communications/communications-dashboard.component').then(
            (component) => component.CommunicationsDashboardComponent,
          ),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/documents/documents-dashboard.component').then(
            (component) => component.DocumentsDashboardComponent,
          ),
      },
      {
        path: 'signatures',
        loadComponent: () =>
          import('./features/documents/document-signatures-dashboard.component').then(
            (component) => component.DocumentSignaturesDashboardComponent,
          ),
      },
      {
        path: 'transport',
        loadComponent: () =>
          import('./features/transport/transport-dashboard.component').then(
            (component) => component.TransportDashboardComponent,
          ),
      },
      {
        path: 'cafeteria',
        loadComponent: () =>
          import('./features/services/cafeteria-dashboard.component').then(
            (component) => component.CafeteriaDashboardComponent,
          ),
      },
      {
        path: 'activities',
        loadComponent: () =>
          import('./features/services/activities-dashboard.component').then(
            (component) => component.ActivitiesDashboardComponent,
          ),
      },
      {
        path: 'health',
        loadComponent: () =>
          import('./features/health/health-dashboard.component').then(
            (component) => component.HealthDashboardComponent,
          ),
      },
      {
        path: 'hr',
        loadComponent: () =>
          import('./features/hr/human-resources-dashboard.component').then(
            (component) => component.HumanResourcesDashboardComponent,
          ),
      },
      {
        path: 'payroll',
        loadComponent: () =>
          import('./features/payroll/payroll-dashboard.component').then(
            (component) => component.PayrollDashboardComponent,
          ),
      },
      {
        path: 'accounting',
        loadComponent: () =>
          import('./features/accounting/accounting-dashboard.component').then(
            (component) => component.AccountingDashboardComponent,
          ),
      },
      {
        path: 'procurement',
        loadComponent: () =>
          import('./features/procurement/procurement-dashboard.component').then(
            (component) => component.ProcurementDashboardComponent,
          ),
      },
      {
        path: 'inventory',
        loadComponent: () =>
          import('./features/inventory/inventory-dashboard.component').then(
            (component) => component.InventoryDashboardComponent,
          ),
      },
      {
        path: 'library',
        loadComponent: () =>
          import('./features/library/library-dashboard.component').then(
            (component) => component.LibraryDashboardComponent,
          ),
      },
      {
        path: 'analytics',
        loadComponent: () =>
          import('./features/analytics/direction-analytics.component').then(
            (component) => component.DirectionAnalyticsComponent,
          ),
      },
      {
        path: 'early-warning',
        loadComponent: () =>
          import('./features/early-warning/early-warning-dashboard.component').then(
            (component) => component.EarlyWarningDashboardComponent,
          ),
      },
      {
        path: 'workflows',
        canActivate: [technicalFeatureGuard],
        loadComponent: () =>
          import('./features/workflow/workflow-studio.component').then(
            (component) => component.WorkflowStudioComponent,
          ),
      },
      {
        path: 'integrations',
        canActivate: [technicalFeatureGuard],
        loadComponent: () =>
          import('./features/integrations/integration-hub.component').then(
            (component) => component.IntegrationHubComponent,
          ),
      },
      {
        path: 'custom-fields',
        loadComponent: () =>
          import('./features/custom-fields/custom-field-studio.component').then(
            (component) => component.CustomFieldStudioComponent,
          ),
      },
      {
        path: 'configuration',
        loadComponent: () =>
          import('./features/business-configuration/business-configuration-center.component').then(
            (component) => component.BusinessConfigurationCenterComponent,
          ),
      },
      {
        path: 'api-access',
        canActivate: [technicalFeatureGuard],
        loadComponent: () =>
          import('./features/public-api/public-api-center.component').then(
            (component) => component.PublicApiCenterComponent,
          ),
      },
      {
        path: 'webhooks',
        canActivate: [technicalFeatureGuard],
        loadComponent: () =>
          import('./features/webhooks/webhook-center.component').then(
            (component) => component.WebhookCenterComponent,
          ),
      },
      {
        path: 'search',
        loadComponent: () =>
          import('./features/search/global-search.component').then(
            (component) => component.GlobalSearchComponent,
          ),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notification-center.component').then(
            (component) => component.NotificationCenterComponent,
          ),
      },
      {
        path: 'tasks',
        loadComponent: () =>
          import('./features/tasks/task-center.component').then(
            (component) => component.TaskCenterComponent,
          ),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/calendar/school-calendar.component').then(
            (component) => component.SchoolCalendarComponent,
          ),
      },
      {
        path: 'privacy',
        loadComponent: () =>
          import('./features/privacy/privacy-center.component').then(
            (component) => component.PrivacyCenterComponent,
          ),
      },
      {
        path: 'imports',
        loadComponent: () =>
          import('./features/imports/data-import.component').then(
            (component) => component.DataImportComponent,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
