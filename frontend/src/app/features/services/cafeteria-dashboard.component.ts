import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CafeteriaDashboard,
  SchoolServicesApiService,
} from '../../core/services/school-services-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
@Component({
  selector: 'app-cafeteria-dashboard',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main>
    <header>
      <p>CANTINE</p>
      <h1>{{ text('Des repas reliés à la finance.', 'وجبات مرتبطة بالمالية.') }}</h1>
      <span>{{
        text(
          'Abonnements, repas ponctuels, menus et données d’allergie protégées.',
          'اشتراكات ووجبات فردية وقوائم وبيانات حساسية محمية.'
        )
      }}</span>
    </header>
    @if (error()) {
      <b class="error">{{ error() }}</b>
    }
    @if (data(); as d) {
      <section class="metrics">
        <article>
          <span>Plans</span><strong>{{ d.plans.length }}</strong>
        </article>
        <article>
          <span>Menus</span><strong>{{ d.menus.length }}</strong>
        </article>
        <article>
          <span>{{ text('Abonnés', 'المشتركون') }}</span
          ><strong>{{ d.subscriptions.length }}</strong>
        </article>
        <article>
          <span>{{ text('Repas servis', 'الوجبات المقدمة') }}</span
          ><strong>{{ d.attendances.length }}</strong>
        </article>
      </section>
      <section class="grid">
        <article>
          <h2>{{ text('Menu publié', 'القائمة المنشورة') }}</h2>
          @for (m of d.menus; track m.id) {
            <h3>{{ m.title }}</h3>
            <p>{{ m.description }}</p>
            <small>{{ m.serviceDate | date: 'dd/MM/yyyy' }} · {{ m.mealType }}</small>
          }
        </article>
        <article>
          <h2>{{ text('Formules & finance', 'الصيغ والمالية') }}</h2>
          @for (p of d.plans; track p.id) {
            <div class="row">
              <strong>{{ p.name }}</strong
              ><span>{{ p.monthlyPrice ?? p.singleMealPrice }} MAD · {{ p.type }}</span>
            </div>
          }
          <p class="finance">✓ billing_charge automatique · service CAFETERIA</p>
        </article>
        <article>
          <h2>{{ text('Présences repas', 'حضور الوجبات') }}</h2>
          @for (a of d.attendances; track a.id) {
            <div class="row">
              <strong>{{ a.studentName }}</strong
              ><span>{{ a.menuTitle }} · {{ a.occurredAt | date: 'HH:mm' }}</span>
            </div>
          }
        </article>
        <article class="health">
          <h2>
            {{ text('Restrictions alimentaires', 'القيود الغذائية') }}
            <small>cafeteria.health</small>
          </h2>
          @if (d.healthDataVisible) {
            @for (r of d.restrictions; track r.id) {
              <div class="restriction">
                <b>{{ r.severity }}</b
                ><strong>{{ r.studentName }} · {{ r.allergen }}</strong
                ><span>{{ r.details }} · {{ r.emergencyAction }}</span>
              </div>
            }
          } @else {
            <p>
              {{
                text(
                  'Données masquées : permission renforcée requise.',
                  'البيانات محجوبة: يلزم إذن معزز.'
                )
              }}
            </p>
          }
        </article>
      </section>
    }
  </main>`,
  styles: [
    `
      :host {
        display: block;
      }
      main {
        min-height: 100%;
        padding: clamp(1rem, 3vw, 2.5rem);
        background: #f7f2e9;
        color: #30271d;
      }
      header p {
        color: #a95b31;
        font-size: 0.7rem;
        font-weight: 850;
        letter-spacing: 0.15em;
      }
      header h1 {
        margin: 0.4rem 0;
        font-size: clamp(2.5rem, 5vw, 4.8rem);
        line-height: 0.95;
        letter-spacing: -0.055em;
      }
      header span {
        color: #756b60;
      }
      .metrics {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 0.6rem;
        margin: 1.2rem 0;
      }
      .metrics article,
      .grid > article {
        padding: 1rem;
        background: #fff;
        border: 1px solid #e2d9cc;
      }
      .metrics article {
        display: grid;
        border-top: 3px solid #b96638;
      }
      .metrics span {
        color: #85796c;
        font-size: 0.75rem;
      }
      .metrics strong {
        font-size: 1.5rem;
      }
      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.7rem;
      }
      .grid h2 {
        font-size: 1rem;
      }
      .row {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.6rem 0;
        border-bottom: 1px solid #eee7dc;
      }
      .row span {
        color: #7d7165;
      }
      .finance {
        padding: 0.6rem;
        background: #e9edf2;
        color: #1d3a6d;
      }
      .health {
        border-top: 4px solid #9b3f35 !important;
      }
      .health h2 {
        display: flex;
        justify-content: space-between;
      }
      .health h2 small {
        color: #9b3f35;
      }
      .restriction {
        display: grid;
        gap: 0.25rem;
        padding: 0.7rem;
        background: #faece8;
      }
      .restriction b {
        color: #a03d33;
      }
      .error {
        display: block;
        padding: 0.7rem;
        background: #fae4df;
      }
      @media (max-width: 700px) {
        .metrics,
        .grid {
          grid-template-columns: 1fr 1fr;
        }
      }
      @media (max-width: 480px) {
        .metrics,
        .grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class CafeteriaDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(SchoolServicesApiService);
  private readonly destroy = inject(DestroyRef);
  protected readonly data = signal<CafeteriaDashboard | null>(null);
  protected readonly error = signal('');
  constructor() {
    this.api
      .cafeteria(true)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (d) => this.data.set(d),
        error: (r) => this.error.set(r.error?.detail ?? 'Cantine indisponible'),
      });
  }
  protected text(f: string, a: string) {
    return this.i18n.locale() === 'ar-MA' ? a : f;
  }
}
