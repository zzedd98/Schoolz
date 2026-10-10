import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import {
  ActivityDashboard,
  CafeteriaDashboard,
  SchoolServicesApiService,
} from '../../core/services/school-services-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
@Component({
  selector: 'app-school-services-portal',
  imports: [DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main [attr.dir]="i18n.locale() === 'ar-MA' ? 'rtl' : 'ltr'">
    <header>
      <a routerLink="/family"><b>SZ</b> SchoolZ</a>
      <nav>
        <a routerLink="/family">{{ text('Accueil', 'الرئيسية') }}</a
        ><a routerLink="/cafeteria">{{ text('Cantine', 'المطعم') }}</a
        ><a routerLink="/activities">{{ text('Activités', 'الأنشطة') }}</a>
      </nav>
      <button type="button" (click)="toggle()">{{ i18n.locale() === 'ar-MA' ? 'FR' : 'ع' }}</button>
    </header>
    @if (auth()) {
      <section class="login">
        <div aria-busy="true">
          <p>FAMILY SERVICES</p>
          <h1>{{ text('Session expirée', 'انتهت صلاحية الجلسة') }}</h1>
          <p>
            {{
              text(
                'Redirection vers la connexion sécurisée…',
                'جار التحويل إلى صفحة الدخول الآمنة…'
              )
            }}
          </p>
        </div>
      </section>
    } @else if (mode === 'cafeteria' && cafeteria(); as d) {
      <section class="hero">
        <p>CANTINE</p>
        <h1>{{ text('Cantine de mes enfants', 'مطعم أطفالي') }}</h1>
        <span>{{
          text('Menus, abonnements et repas servis.', 'القوائم والاشتراكات والوجبات المقدمة.')
        }}</span>
      </section>
      <section class="cards">
        @for (m of d.menus; track m.id) {
          <article>
            <b>{{ m.serviceDate | date: 'dd/MM' }}</b>
            <h2>{{ m.title }}</h2>
            <p>{{ m.description }}</p>
          </article>
        }
        @for (s of d.subscriptions; track s.id) {
          <article>
            <b>{{ s.status }}</b>
            <h2>{{ s.studentName }}</h2>
            <p>{{ s.planName }} · {{ s.billingMode }}</p>
            <small>✓ {{ text('Relié à Finance', 'مرتبط بالمالية') }}</small>
          </article>
        }
        @for (r of d.restrictions; track r.id) {
          <article class="health">
            <b>{{ r.severity }}</b>
            <h2>{{ r.studentName }} · {{ r.allergen }}</h2>
            <p>{{ r.details }}</p>
            <small>{{ r.emergencyAction }}</small>
          </article>
        }
      </section>
    } @else if (mode === 'activities' && activities(); as d) {
      <section class="hero">
        <p>ACTIVITÉS</p>
        <h1>{{ text('Activités extrascolaires', 'الأنشطة الموازية') }}</h1>
        <span>{{
          text(
            'Inscription autorisée, capacité et liste d’attente vérifiées en temps réel.',
            'تسجيل مصرح به مع مراقبة السعة ولائحة الانتظار فوراً.'
          )
        }}</span>
      </section>
      <section class="cards">
        @for (a of d.activities; track a.id) {
          <article>
            <b>{{ a.category }}</b>
            <h2>{{ a.name }}</h2>
            <p>
              {{ a.coachName }} · {{ a.enrolledCount }}/{{ a.maximumCapacity }} · {{ a.price }} MAD
            </p>
            <small>{{ a.waitingCount }} {{ text('en attente', 'في الانتظار') }}</small
            ><button
              type="button"
              (click)="enroll(a.id)"
              [disabled]="!a.allowParentEnrollment || !d.students[0]"
            >
              {{
                text(
                  'Inscrire ' + (d.students[0]?.displayName ?? ''),
                  'تسجيل ' + (d.students[0]?.displayName ?? '')
                )
              }}
            </button>
          </article>
        }
        @for (e of d.enrollments; track e.id) {
          <article class="enrolled">
            <b>✓ ENROLLED</b>
            <h2>{{ e.activityName }}</h2>
            <p>{{ e.studentName }} · {{ e.enrolledAt | date: 'dd/MM/yyyy' }}</p>
          </article>
        }
      </section>
    }
    @if (message()) {
      <p class="message">{{ message() }}</p>
    }
  </main>`,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
      }
      main {
        min-height: 100vh;
        padding: 0 clamp(1rem, 5vw, 5rem) 3rem;
        background: #f0f2f5;
        color: #10203d;
      }
      header {
        height: 72px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid #d6dade;
      }
      header a {
        text-decoration: none;
        color: inherit;
        font-weight: 800;
      }
      header b {
        display: inline-grid;
        place-items: center;
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: #1a3461;
        color: #fff;
        font-size: 0.7rem;
      }
      nav {
        display: flex;
        gap: 1.2rem;
      }
      header button {
        border: 1px solid #cacfd5;
        background: white;
        border-radius: 50%;
        width: 34px;
        height: 34px;
      }
      .hero {
        padding: clamp(2.5rem, 7vw, 5.5rem) 0 2rem;
      }
      .hero p,
      .login p {
        color: #224480;
        font-size: 0.7rem;
        font-weight: 850;
        letter-spacing: 0.15em;
      }
      .hero h1,
      .login h1 {
        margin: 0.4rem 0;
        font-size: clamp(2.7rem, 6vw, 5.7rem);
        line-height: 0.94;
        letter-spacing: -0.06em;
      }
      .hero span {
        color: #5f6d84;
      }
      .cards {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.7rem;
      }
      .cards article {
        padding: 1rem;
        background: white;
        border-top: 3px solid #2b57a4;
      }
      .cards article.health {
        border-color: #a04738;
        background: #fff8f5;
      }
      .cards article.enrolled {
        background: #e8edf2;
      }
      .cards b {
        color: #234785;
        font-size: 0.7rem;
      }
      .cards p {
        color: #616e85;
      }
      .cards small {
        display: block;
        color: #576378;
      }
      .cards button {
        width: 100%;
        margin-top: 0.7rem;
        padding: 0.65rem;
        border: 0;
        background: #1a3461;
        color: white;
      }
      .cards button:disabled {
        opacity: 0.45;
      }
      .login {
        min-height: calc(100vh - 72px);
        display: grid;
        grid-template-columns: 1.1fr 0.9fr;
        align-items: center;
        gap: 5vw;
      }
      .login form {
        display: grid;
        gap: 0.7rem;
        max-width: 420px;
        width: 100%;
        justify-self: end;
        padding: 1.4rem;
        background: white;
        border-top: 4px solid #234785;
      }
      .login input,
      .login button {
        padding: 0.75rem;
        border: 1px solid #cacfd5;
      }
      .login button {
        background: #1a3461;
        color: #fff;
      }
      .message {
        position: fixed;
        right: 1rem;
        bottom: 1rem;
        padding: 1rem;
        background: #132748;
        color: #fff;
      }
      @media (max-width: 850px) {
        .cards {
          grid-template-columns: 1fr 1fr;
        }
        .login {
          grid-template-columns: 1fr;
        }
      }
      @media (max-width: 560px) {
        nav {
          display: none;
        }
        .cards {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class SchoolServicesPortalComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(SchoolServicesApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly router = inject(Router);
  protected readonly cafeteria = signal<CafeteriaDashboard | null>(null);
  protected readonly activities = signal<ActivityDashboard | null>(null);
  protected readonly auth = signal(false);
  protected readonly message = signal('');
  protected readonly mode = this.router.url.includes('activities') ? 'activities' : 'cafeteria';
  constructor() {
    this.load();
  }
  protected text(f: string, a: string) {
    return this.i18n.locale() === 'ar-MA' ? a : f;
  }
  protected toggle() {
    this.i18n.setLocale(this.i18n.locale() === 'ar-MA' ? 'fr-MA' : 'ar-MA');
  }
  protected enroll(activityId: string) {
    const student = this.activities()?.students[0];
    if (!student) return;
    this.api
      .enroll(activityId, student.id)
      .pipe(
        switchMap((r) => {
          this.message.set(
            r.status === 'WAITING_LIST'
              ? this.text(
                  'Ajouté à la liste d’attente #' + r.waitingPosition,
                  'تمت الإضافة إلى لائحة الانتظار #' + r.waitingPosition,
                )
              : this.text(
                  'Inscription confirmée et charge Finance créée.',
                  'تم تأكيد التسجيل وإنشاء الرسوم المالية.',
                ),
          );
          return this.api.activities();
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((d) => this.activities.set(d));
  }
  private load() {
    const q: Observable<ActivityDashboard | CafeteriaDashboard> =
      this.mode === 'activities' ? this.api.activities() : this.api.cafeteria();
    q.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (d) => this.accept(d),
      error: (r: { status?: number }) => {
        this.auth.set(r.status === 401);
        if (r.status === 401) {
          void this.router.navigate(['/login'], {
            queryParams: { portal: 'family', returnUrl: this.router.url },
            replaceUrl: true,
          });
        }
      },
    });
  }
  private accept(d: ActivityDashboard | CafeteriaDashboard) {
    this.auth.set(false);
    if (this.mode === 'activities') this.activities.set(d as ActivityDashboard);
    else this.cafeteria.set(d as CafeteriaDashboard);
  }
}
