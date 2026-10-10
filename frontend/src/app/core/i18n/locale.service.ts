import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

import { arabicTranslations, frenchTranslations, TranslationKey } from './translations';

export type LocaleCode = 'fr-MA' | 'ar-MA';
export type { TranslationKey } from './translations';

const STORAGE_KEY = 'schoolz.locale';

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly document = inject(DOCUMENT);

  readonly locale = signal<LocaleCode>(this.initialLocale());
  readonly direction = computed(() => (this.locale() === 'ar-MA' ? 'rtl' : 'ltr'));

  constructor() {
    effect(() => {
      const locale = this.locale();
      this.document.documentElement.lang = locale;
      this.document.documentElement.dir = this.direction();
      this.persist(locale);
    });
  }

  setLocale(locale: LocaleCode): void {
    this.locale.set(locale);
  }

  translate(key: TranslationKey): string {
    return this.locale() === 'ar-MA' ? arabicTranslations[key] : frenchTranslations[key];
  }

  private initialLocale(): LocaleCode {
    try {
      const saved = globalThis.localStorage?.getItem(STORAGE_KEY);
      if (saved === 'fr-MA' || saved === 'ar-MA') {
        return saved;
      }
    } catch {
      // Storage may be unavailable in hardened browsing contexts.
    }
    return globalThis.navigator?.language?.startsWith('ar') ? 'ar-MA' : 'fr-MA';
  }

  private persist(locale: LocaleCode): void {
    try {
      globalThis.localStorage?.setItem(STORAGE_KEY, locale);
    } catch {
      // Locale remains available in memory when storage is blocked.
    }
  }
}
