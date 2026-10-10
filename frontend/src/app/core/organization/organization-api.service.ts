import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface OrganizationView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly legalName: string | null;
  readonly logoObjectKey: string | null;
  readonly addressLine1: string | null;
  readonly addressLine2: string | null;
  readonly city: string | null;
  readonly region: string | null;
  readonly postalCode: string | null;
  readonly countryCode: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly businessIdentifiers: Readonly<Record<string, string>>;
}

export interface SchoolSettingsView {
  readonly id: string;
  readonly defaultLocale: 'fr-MA' | 'ar-MA';
  readonly supportedLocales: readonly string[];
  readonly currencyCode: string;
  readonly timezone: string;
  readonly weekStartsOn: number;
  readonly workingDays: readonly string[];
  readonly dayStartsAt: string;
  readonly dayEndsAt: string;
  readonly calendarNotes: string | null;
}

export interface CampusView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly addressLine1: string | null;
  readonly addressLine2: string | null;
  readonly city: string | null;
  readonly region: string | null;
  readonly postalCode: string | null;
  readonly countryCode: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly locale: 'fr-MA' | 'ar-MA';
  readonly timezone: string;
  readonly active: boolean;
  readonly offeredGradeLevelIds: readonly string[];
}

export interface AcademicTermView {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly type: 'TERM' | 'SEMESTER' | 'TRIMESTER';
  readonly sequence: number;
  readonly startsOn: string;
  readonly endsOn: string;
}

export interface AcademicYearView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly status: 'DRAFT' | 'ACTIVE' | 'CLOSED';
  readonly terms: readonly AcademicTermView[];
}

export interface GradeLevelView {
  readonly id: string;
  readonly schoolLevelId: string;
  readonly code: string;
  readonly name: string;
  readonly sequence: number;
  readonly active: boolean;
}

export interface SchoolLevelView {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly sequence: number;
  readonly active: boolean;
  readonly grades: readonly GradeLevelView[];
}

export interface PhysicalRoomView {
  readonly id: string;
  readonly campusId: string;
  readonly code: string;
  readonly name: string;
  readonly roomType: string;
  readonly capacity: number;
  readonly floorLabel: string | null;
  readonly active: boolean;
}

export interface ClassroomView {
  readonly id: string;
  readonly campusId: string;
  readonly academicYearId: string;
  readonly gradeLevelId: string;
  readonly physicalRoomId: string | null;
  readonly code: string;
  readonly name: string;
  readonly capacity: number;
  readonly active: boolean;
}

export type OrganizationRequest = Omit<OrganizationView, 'id' | 'businessIdentifiers'> & {
  readonly businessIdentifiers: Readonly<Record<string, string>>;
};

export type CampusRequest = Omit<CampusView, 'id' | 'active' | 'offeredGradeLevelIds'>;

@Injectable({ providedIn: 'root' })
export class OrganizationApiService {
  private readonly http = inject(HttpClient);

  organization() {
    return this.http.get<OrganizationView>('/api/v1/organization');
  }

  configureOrganization(request: OrganizationRequest) {
    return this.http.put<OrganizationView>('/api/v1/organization', request);
  }

  settings() {
    return this.http.get<SchoolSettingsView>('/api/v1/organization/settings');
  }

  configureSettings(request: Omit<SchoolSettingsView, 'id'>) {
    return this.http.put<SchoolSettingsView>('/api/v1/organization/settings', request);
  }

  campuses() {
    return this.http.get<readonly CampusView[]>('/api/v1/campuses');
  }

  createCampus(request: CampusRequest) {
    return this.http.post<CampusView>('/api/v1/campuses', request);
  }

  setCampusOfferings(campusId: string, gradeLevelIds: readonly string[]) {
    return this.http.put<CampusView>(`/api/v1/campuses/${campusId}/grade-levels`, {
      gradeLevelIds,
    });
  }

  academicYears() {
    return this.http.get<readonly AcademicYearView[]>('/api/v1/academics/years');
  }

  createAcademicYear(request: { code: string; name: string; startsOn: string; endsOn: string }) {
    return this.http.post<AcademicYearView>('/api/v1/academics/years', request);
  }

  createTerm(
    yearId: string,
    request: {
      code: string;
      name: string;
      type: 'TERM' | 'SEMESTER' | 'TRIMESTER';
      sequence: number;
      startsOn: string;
      endsOn: string;
    },
  ) {
    return this.http.post<AcademicTermView>(`/api/v1/academics/years/${yearId}/terms`, request);
  }

  activateAcademicYear(yearId: string) {
    return this.http.patch<AcademicYearView>(`/api/v1/academics/years/${yearId}/status`, {
      status: 'ACTIVE',
    });
  }

  schoolLevels() {
    return this.http.get<readonly SchoolLevelView[]>('/api/v1/academics/levels');
  }

  createSchoolLevel(request: { code: string; name: string; sequence: number }) {
    return this.http.post<SchoolLevelView>('/api/v1/academics/levels', request);
  }

  createGradeLevel(levelId: string, request: { code: string; name: string; sequence: number }) {
    return this.http.post<GradeLevelView>(`/api/v1/academics/levels/${levelId}/grades`, request);
  }

  rooms() {
    return this.http.get<readonly PhysicalRoomView[]>('/api/v1/academics/rooms');
  }

  createRoom(request: {
    campusId: string;
    code: string;
    name: string;
    roomType: string;
    capacity: number;
    floorLabel: string;
  }) {
    return this.http.post<PhysicalRoomView>('/api/v1/academics/rooms', request);
  }

  classrooms() {
    return this.http.get<readonly ClassroomView[]>('/api/v1/academics/classrooms');
  }

  createClassroom(request: {
    campusId: string;
    academicYearId: string;
    gradeLevelId: string;
    physicalRoomId: string | null;
    code: string;
    name: string;
    capacity: number;
  }) {
    return this.http.post<ClassroomView>('/api/v1/academics/classrooms', request);
  }
}
