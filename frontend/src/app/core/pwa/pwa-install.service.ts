import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';

interface InstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export type PwaPortal = 'family' | 'teacher';
export type InstallResult = 'accepted' | 'dismissed' | 'unavailable';

@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private readonly document = inject(DOCUMENT);
  private promptEvent: InstallPromptEvent | null = null;

  readonly standalone = signal(
    globalThis.matchMedia?.('(display-mode: standalone)').matches ?? false,
  );
  readonly installAvailable = signal(false);

  constructor() {
    globalThis.addEventListener?.('beforeinstallprompt', this.capturePrompt);
    globalThis.addEventListener?.('appinstalled', this.captureInstalled);
  }

  activate(portal: PwaPortal): void {
    const manifest = this.document.querySelector<HTMLLinkElement>('link[rel="manifest"]');
    manifest?.setAttribute('href', `manifest-${portal}.webmanifest`);
  }

  async install(): Promise<InstallResult> {
    if (!this.promptEvent) return 'unavailable';
    await this.promptEvent.prompt();
    const { outcome } = await this.promptEvent.userChoice;
    this.promptEvent = null;
    this.installAvailable.set(false);
    if (outcome === 'accepted') this.standalone.set(true);
    return outcome;
  }

  private readonly capturePrompt = (rawEvent: Event): void => {
    rawEvent.preventDefault();
    this.promptEvent = rawEvent as InstallPromptEvent;
    this.installAvailable.set(true);
  };

  private readonly captureInstalled = (): void => {
    this.promptEvent = null;
    this.installAvailable.set(false);
    this.standalone.set(true);
  };
}
