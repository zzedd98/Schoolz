import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivityDashboard,
  SchoolServicesApiService,
} from '../../core/services/school-services-api.service';
import { LocaleService } from '../../core/i18n/locale.service';
@Component({
  selector: 'app-activities-dashboard',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main>
    <header>
      <p>ACTIVITÉS EXTRASCOLAIRES</p>
      <h1>{{ text('Capacité maîtrisée, talents libérés.', 'سعة مضبوطة ومواهب حرة.') }}</h1>
      <span>{{
        text(
          'Football, natation, robotique, musique et garderie, avec finance et liste d’attente.',
          'كرة القدم والسباحة والروبوتيك والموسيقى والحضانة مع المالية ولائحة الانتظار.'
        )
      }}</span>
    </header>
    @if (data(); as d) {
      <section>
        @for (a of d.activities; track a.id) {
          <article>
            <div class="top">
              <b>{{ a.category }}</b
              ><span>{{ a.status }}</span>
            </div>
            <h2>{{ a.name }}</h2>
            <p>{{ a.description }}</p>
            <div class="capacity">
              <strong>{{ a.enrolledCount }} / {{ a.maximumCapacity }}</strong
              ><i [style.width.%]="(a.enrolledCount / a.maximumCapacity) * 100"></i>
            </div>
            <dl>
              <div>
                <dt>Coach</dt>
                <dd>{{ a.coachName }}</dd>
              </div>
              <div>
                <dt>{{ text('Prix', 'السعر') }}</dt>
                <dd>{{ a.price }} MAD</dd>
              </div>
              <div>
                <dt>{{ text('Attente', 'الانتظار') }}</dt>
                <dd>{{ a.waitingCount }}</dd>
              </div>
            </dl>
            @for (s of a.sessions; track s.id) {
              <small>{{ s.startsAt | date: 'dd/MM HH:mm' }} · {{ s.location }}</small>
            }
            <footer>
              ✓ {{ text('Inscription parent', 'تسجيل الأسرة') }} · ✓ billing_charge ACTIVITY
            </footer>
          </article>
        }
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
        background: #eef0f2;
        color: #10203c;
      }
      header p {
        color: #234683;
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
        color: #5d6a81;
      }
      section {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.7rem;
        margin-top: 1.3rem;
      }
      article {
        padding: 1rem;
        background: #fff;
        border: 1px solid #d9dde2;
      }
      .top {
        display: flex;
        justify-content: space-between;
      }
      .top b {
        color: #244989;
        font-size: 0.7rem;
      }
      .top span {
        font-size: 0.65rem;
        background: #e7ebf0;
        padding: 0.2rem 0.4rem;
      }
      h2 {
        margin: 0.7rem 0 0.3rem;
      }
      article > p {
        min-height: 2.5em;
        color: #616f87;
      }
      .capacity {
        position: relative;
        padding: 0.6rem;
        background: #edeff1;
        overflow: hidden;
      }
      .capacity i {
        position: absolute;
        bottom: 0;
        left: 0;
        height: 3px;
        background: #274e92;
      }
      dl {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.3rem;
      }
      dl div {
        padding: 0.5rem;
        background: #f3f4f6;
      }
      dt {
        font-size: 0.65rem;
        color: #67758e;
      }
      dd {
        margin: 0.2rem 0 0;
        font-weight: 800;
      }
      article > small {
        display: block;
        padding: 0.45rem 0;
        color: #5a677d;
      }
      footer {
        margin-top: 0.6rem;
        padding-top: 0.6rem;
        border-top: 1px solid #e2e5e9;
        color: #234784;
        font-size: 0.7rem;
      }
      @media (max-width: 950px) {
        section {
          grid-template-columns: 1fr 1fr;
        }
      }
      @media (max-width: 580px) {
        section {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ActivitiesDashboardComponent {
  protected readonly i18n = inject(LocaleService);
  private readonly api = inject(SchoolServicesApiService);
  private readonly destroy = inject(DestroyRef);
  protected readonly data = signal<ActivityDashboard | null>(null);
  constructor() {
    this.api
      .activities(true)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe((d) => this.data.set(d));
  }
  protected text(f: string, a: string) {
    return this.i18n.locale() === 'ar-MA' ? a : f;
  }
}
