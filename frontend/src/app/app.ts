import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { RouteAccessibilityService } from './core/a11y/route-accessibility.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly routeAccessibility = inject(RouteAccessibilityService);

  constructor() {
    this.routeAccessibility.start();
  }
}
