import { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  production: false,
  demoAccounts: {
    tenantSlug: 'ecole-demo',
    admin: { email: 'admin@schoolz.ma', password: 'SchoolZDemo2026!' },
    teacher: { email: 'prof.math@schoolz.ma', password: 'SchoolZTeacher2026!' },
    student: { email: 'yasmine.eleve@schoolz.ma', password: 'SchoolZStudent2026!' },
    parent: { email: 'parent.demo@schoolz.ma', password: 'SchoolZParent2026!' },
  },
  sandboxPayments: true,
  showTechnicalFeatures: true,
};
