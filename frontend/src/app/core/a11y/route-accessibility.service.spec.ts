import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { RouteAccessibilityService } from './route-accessibility.service';

describe('RouteAccessibilityService', () => {
  it('focuses and announces a heading that appears after asynchronous route data', () => {
    vi.useFakeTimers();
    const events = new Subject<NavigationEnd>();
    document.body.innerHTML = '<div id="route-announcer"></div><main><h1>Mes élèves</h1></main>';
    TestBed.configureTestingModule({
      providers: [
        RouteAccessibilityService,
        { provide: Router, useValue: { events } },
        { provide: DOCUMENT, useValue: document },
      ],
    });

    TestBed.inject(RouteAccessibilityService).start();
    events.next(new NavigationEnd(1, '/students', '/students'));
    globalThis.setTimeout(() => {
      document.querySelector('main')!.innerHTML = '<h1>Mon espace élève</h1>';
    }, 120);
    vi.runAllTimers();

    expect(document.activeElement?.textContent).toBe('Mon espace élève');
    expect(document.title).toBe('Mon espace élève — SchoolZ');
    expect(document.getElementById('route-announcer')?.textContent).toBe('Page Mon espace élève');
    vi.useRealTimers();
  });
});
