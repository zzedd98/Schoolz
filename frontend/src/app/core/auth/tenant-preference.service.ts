import { Injectable } from '@angular/core';

const STORAGE_KEY = 'schoolz.lastTenant';
const TENANT_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

@Injectable({ providedIn: 'root' })
export class TenantPreferenceService {
  read(): string {
    try {
      const value = globalThis.localStorage?.getItem(STORAGE_KEY)?.trim().toLowerCase() ?? '';
      return TENANT_PATTERN.test(value) ? value : '';
    } catch {
      return '';
    }
  }

  remember(tenantSlug: string): void {
    const value = tenantSlug.trim().toLowerCase();
    if (!TENANT_PATTERN.test(value)) return;
    try {
      globalThis.localStorage?.setItem(STORAGE_KEY, value);
    } catch {
      // The login remains usable when storage is disabled or unavailable.
    }
  }
}
