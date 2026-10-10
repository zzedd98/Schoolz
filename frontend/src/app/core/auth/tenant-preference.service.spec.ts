import { TestBed } from '@angular/core/testing';

import { TenantPreferenceService } from './tenant-preference.service';

describe('TenantPreferenceService', () => {
  let service: TenantPreferenceService;

  beforeEach(() => {
    globalThis.localStorage.clear();
    service = TestBed.inject(TenantPreferenceService);
  });

  it('remembers only a normalized non-sensitive tenant identifier', () => {
    service.remember('  Ecole-Demo  ');

    expect(service.read()).toBe('ecole-demo');
    expect(globalThis.localStorage.getItem('schoolz.lastTenant')).toBe('ecole-demo');
  });

  it('ignores malformed cached values', () => {
    globalThis.localStorage.setItem('schoolz.lastTenant', 'admin@schoolz.ma');

    expect(service.read()).toBe('');
  });
});
