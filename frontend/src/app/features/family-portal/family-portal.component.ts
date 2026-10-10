import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AdmissionPortalApiService } from '../../core/admissions/admission-portal-api.service';
import {
  FamilyAbsence,
  FamilyChild,
  FamilyGrade,
  FamilyModule,
  FamilyPortalApiService,
  FamilyReportCard,
  FamilyTimetableLesson,
} from '../../core/family-portal/family-portal-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { PwaInstallService } from '../../core/pwa/pwa-install.service';

@Component({
  selector: 'app-family-portal',
  templateUrl: './family-portal.component.html',
  styleUrl: './family-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(FamilyPortalApiService);
  private readonly admissions = inject(AdmissionPortalApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pwa = inject(PwaInstallService);

  protected readonly dashboard = signal<
    import('../../core/family-portal/family-portal-api.service').FamilyDashboard | null
  >(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly installHint = signal(false);
  protected readonly standalone = this.pwa.standalone;
  protected readonly selectedChild = computed(() => {
    const data = this.dashboard();
    return data?.children.find((child) => child.id === data.selectedStudentId) ?? null;
  });

  constructor() {
    this.pwa.activate('family');
    this.load();
  }

  protected text(french: string, arabic: string): string {
    return this.i18n.locale() === 'ar-MA' ? arabic : french;
  }

  protected toggleLocale(): void {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }

  protected logout(): void {
    this.busy.set(true);
    this.admissions
      .logout()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.redirectToLogin(),
        error: () => this.redirectToLogin(),
      });
  }

  protected selectChild(studentId: string): void {
    if (!studentId || studentId === this.dashboard()?.selectedStudentId) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .prefer(studentId)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
          this.notice.set(
            this.text(
              `Vous consultez maintenant le dossier de ${this.selectedChild()?.firstName ?? ''}.`,
              `أنتم الآن في ملف ${this.selectedChild()?.firstName ?? ''}.`,
            ),
          );
        },
        error: (response: { error?: { detail?: string } }) =>
          this.error.set(
            response.error?.detail ??
              this.text('Le changement de profil a échoué.', 'تعذر تغيير ملف الطفل.'),
          ),
      });
  }

  protected selectDate(event: Event): void {
    const date = (event.target as HTMLInputElement).value;
    if (date) this.load(this.dashboard()?.selectedStudentId ?? undefined, date);
  }

  protected openModule(module: FamilyModule): void {
    if (!module.available || !module.targetPath) return;
    if (module.targetPath.startsWith('#')) {
      document.getElementById(module.targetPath.slice(1))?.scrollIntoView({ behavior: 'smooth' });
      return;
    }
    void this.router.navigate([module.targetPath], {
      queryParams: { studentId: this.dashboard()?.selectedStudentId },
    });
  }

  protected async install(): Promise<void> {
    this.installHint.set((await this.pwa.install()) === 'unavailable');
  }

  protected retry(): void {
    this.load();
  }

  protected download(report: FamilyReportCard): void {
    const studentId = this.dashboard()?.selectedStudentId;
    if (!studentId) return;
    this.busy.set(true);
    this.api
      .reportCard(studentId, report.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = report.fileName;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: () =>
          this.error.set(
            this.text('Le bulletin ne peut pas être téléchargé.', 'تعذر تنزيل بيان النقط.'),
          ),
      });
  }

  protected moduleLabel(code: FamilyModule['code']): string {
    const labels: Record<FamilyModule['code'], [string, string]> = {
      SCHOOLING: ['Scolarité', 'التمدرس'],
      TIMETABLE: ['Emploi du temps', 'استعمال الزمن'],
      HOMEWORK: ['Devoirs', 'الواجبات'],
      GRADES: ['Notes', 'النقط'],
      REPORT_CARDS: ['Bulletins', 'بيانات النقط'],
      ATTENDANCE: ['Absences', 'الغيابات'],
      JUSTIFICATION: ['Justificatifs', 'التبريرات'],
      CHILDCARE: ['Préscolaire', 'التعليم الأولي'],
      FINANCE: ['Finance', 'المالية'],
      MESSAGES: ['Messages', 'الرسائل'],
      DOCUMENTS: ['Documents', 'الوثائق'],
      TRANSPORT: ['Transport', 'النقل'],
      ACTIVITIES: ['Activités', 'الأنشطة'],
      LIBRARY: ['Bibliothèque', 'المكتبة'],
    };
    return this.i18n.locale() === 'ar-MA' ? labels[code][1] : labels[code][0];
  }

  protected moduleIndex(index: number): string {
    return String(index + 1).padStart(2, '0');
  }

  protected moduleAvailable(code: FamilyModule['code']): boolean {
    return this.dashboard()?.modules.find((module) => module.code === code)?.available ?? false;
  }

  protected attendanceLabel(status: string): string {
    const labels: Record<string, [string, string]> = {
      PRESENT: ['Présent aujourd’hui', 'حاضر اليوم'],
      LATE: ['Arrivée tardive', 'وصول متأخر'],
      EARLY_DEPARTURE: ['Départ anticipé', 'مغادرة مبكرة'],
      ABSENT: ['Absent aujourd’hui', 'غائب اليوم'],
      NO_SESSION: ['Pas encore de séance', 'لا توجد حصة بعد'],
    };
    const label = labels[status] ?? [status, status];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
  }

  protected absenceLabel(status: string): string {
    const labels: Record<string, [string, string]> = {
      UNEXCUSED: ['À justifier', 'تحتاج إلى تبرير'],
      PENDING: ['En vérification', 'قيد المراجعة'],
      EXCUSED: ['Justifiée', 'مبررة'],
      REJECTED: ['À corriger', 'تحتاج إلى تصحيح'],
    };
    const label = labels[status] ?? [status, status];
    return this.i18n.locale() === 'ar-MA' ? label[1] : label[0];
  }

  protected subjectName(
    item:
      FamilyGrade | FamilyTimetableLesson | { subjectNameFr: string; subjectNameAr: string | null },
  ): string {
    return this.i18n.locale() === 'ar-MA' && item.subjectNameAr
      ? item.subjectNameAr
      : item.subjectNameFr;
  }

  protected absenceReason(absence: FamilyAbsence): string {
    return this.i18n.locale() === 'ar-MA' && absence.reasonAr
      ? absence.reasonAr
      : (absence.reasonFr ?? this.text('Motif non renseigné', 'السبب غير محدد'));
  }

  protected initials(child: FamilyChild): string {
    return `${child.firstName.charAt(0)}${child.lastName.charAt(0)}`.toUpperCase();
  }

  protected score(grade: FamilyGrade): string {
    return `${this.number(grade.score)}/${this.number(grade.maximumScore)}`;
  }

  protected number(value: number): string {
    return new Intl.NumberFormat(this.i18n.locale(), { maximumFractionDigits: 2 }).format(value);
  }

  protected formatDate(value: string | null, includeTime = false): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      day: '2-digit',
      month: 'long',
      ...(includeTime ? { hour: '2-digit', minute: '2-digit' } : {}),
    }).format(new Date(value.length === 10 ? `${value}T12:00:00` : value));
  }

  protected time(value: string): string {
    return value.slice(0, 5);
  }

  private load(studentId?: string, date?: string): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .dashboard(studentId, date)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dashboard) => {
          this.dashboard.set(dashboard);
        },
        error: (response: { status?: number; error?: { detail?: string } }) => {
          this.dashboard.set(null);
          if (response.status === 401) {
            this.redirectToLogin(true);
            return;
          }
          this.error.set(
            response.error?.detail ??
              this.text('Votre espace famille ne peut pas être chargé.', 'تعذر تحميل فضاء الأسرة.'),
          );
        },
      });
  }

  private redirectToLogin(familyContext = false): void {
    this.dashboard.set(null);
    this.notice.set('');
    this.error.set('');
    void this.router.navigate(['/login'], {
      ...(familyContext ? { queryParams: { portal: 'family' } } : {}),
      replaceUrl: true,
    });
  }
}
