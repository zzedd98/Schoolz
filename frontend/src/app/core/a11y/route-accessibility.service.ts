import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class RouteAccessibilityService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private navigationSequence = 0;
  private lastAnnouncedHeading: HTMLElement | null = null;

  start(): void {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      const sequence = ++this.navigationSequence;
      for (const delay of [0, 80, 200, 500, 1000, 1600]) {
        globalThis.setTimeout(() => {
          if (sequence === this.navigationSequence) this.announcePage();
        }, delay);
      }
    });
  }

  private announcePage(): void {
    const heading = this.document.querySelector<HTMLElement>('main h1, [role="main"] h1, h1');
    if (!heading || heading === this.lastAnnouncedHeading) return;
    const title = heading.textContent?.replace(/\s+/g, ' ').trim();
    if (!title) return;
    this.lastAnnouncedHeading = heading;
    heading.tabIndex = -1;
    heading.focus({ preventScroll: true });
    this.document.title = `${title} — SchoolZ`;
    const announcer = this.document.getElementById('route-announcer');
    if (announcer) announcer.textContent = `Page ${title}`;
  }
}
