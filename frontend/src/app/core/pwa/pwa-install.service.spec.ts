import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { PwaInstallService } from './pwa-install.service';

describe('PwaInstallService', () => {
  it('activates the manifest dedicated to the current portal', () => {
    const service = TestBed.inject(PwaInstallService);
    const document = TestBed.inject(DOCUMENT);
    const manifest = document.createElement('link');
    manifest.rel = 'manifest';
    document.head.append(manifest);

    service.activate('teacher');

    expect(manifest.getAttribute('href')).toBe('manifest-teacher.webmanifest');
    manifest.remove();
  });

  it('does not fabricate an installation prompt when the browser has none', async () => {
    const service = TestBed.inject(PwaInstallService);

    await expect(service.install()).resolves.toBe('unavailable');
  });
});
