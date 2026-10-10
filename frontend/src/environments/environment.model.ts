export interface DemoAccount {
  readonly email: string;
  readonly password: string;
}

export interface DemoAccounts {
  readonly tenantSlug: string;
  readonly admin: DemoAccount;
  readonly teacher: DemoAccount;
  readonly student: DemoAccount;
  readonly parent: DemoAccount;
}

export interface AppEnvironment {
  readonly production: boolean;
  readonly demoAccounts: DemoAccounts | null;
  readonly sandboxPayments: boolean;
  readonly showTechnicalFeatures: boolean;
}
