import { TestBed } from '@angular/core/testing';

import { LocaleService } from './locale.service';

describe('LocaleService', () => {
  it('switches the document to Arabic and RTL', () => {
    globalThis.localStorage.clear();
    const service = TestBed.inject(LocaleService);

    service.setLocale('ar-MA');
    TestBed.flushEffects();

    expect(document.documentElement.lang).toBe('ar-MA');
    expect(document.documentElement.dir).toBe('rtl');
    expect(service.translate('auth.title')).toContain('عودتك');
  });
});
