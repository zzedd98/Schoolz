package ma.schoolz.erp.organization.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import ma.schoolz.erp.identity.TenantDirectory;
import ma.schoolz.erp.organization.domain.AcademicTerm;
import ma.schoolz.erp.organization.domain.AcademicYear;
import ma.schoolz.erp.organization.domain.AcademicYearStatus;
import ma.schoolz.erp.organization.domain.Campus;
import ma.schoolz.erp.organization.domain.CampusGradeOffering;
import ma.schoolz.erp.organization.domain.Classroom;
import ma.schoolz.erp.organization.domain.GradeLevel;
import ma.schoolz.erp.organization.domain.Organization;
import ma.schoolz.erp.organization.domain.PhysicalRoom;
import ma.schoolz.erp.organization.domain.SchoolLevel;
import ma.schoolz.erp.organization.domain.SchoolSettings;
import ma.schoolz.erp.organization.domain.TermType;
import ma.schoolz.erp.organization.persistence.AcademicTermRepository;
import ma.schoolz.erp.organization.persistence.AcademicYearRepository;
import ma.schoolz.erp.organization.persistence.CampusGradeOfferingRepository;
import ma.schoolz.erp.organization.persistence.CampusRepository;
import ma.schoolz.erp.organization.persistence.ClassroomRepository;
import ma.schoolz.erp.organization.persistence.GradeLevelRepository;
import ma.schoolz.erp.organization.persistence.OrganizationRepository;
import ma.schoolz.erp.organization.persistence.PhysicalRoomRepository;
import ma.schoolz.erp.organization.persistence.SchoolLevelRepository;
import ma.schoolz.erp.organization.persistence.SchoolSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(20)
public class DevOrganizationSeeder implements ApplicationRunner {

    private final TenantDirectory tenantDirectory;
    private final OrganizationRepository organizations;
    private final SchoolSettingsRepository settings;
    private final CampusRepository campuses;
    private final AcademicYearRepository years;
    private final AcademicTermRepository terms;
    private final SchoolLevelRepository levels;
    private final GradeLevelRepository grades;
    private final CampusGradeOfferingRepository offerings;
    private final PhysicalRoomRepository rooms;
    private final ClassroomRepository classrooms;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevOrganizationSeeder(
            TenantDirectory tenantDirectory,
            OrganizationRepository organizations,
            SchoolSettingsRepository settings,
            CampusRepository campuses,
            AcademicYearRepository years,
            AcademicTermRepository terms,
            SchoolLevelRepository levels,
            GradeLevelRepository grades,
            CampusGradeOfferingRepository offerings,
            PhysicalRoomRepository rooms,
            ClassroomRepository classrooms,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.tenantDirectory = tenantDirectory;
        this.organizations = organizations;
        this.settings = settings;
        this.campuses = campuses;
        this.years = years;
        this.terms = terms;
        this.levels = levels;
        this.grades = grades;
        this.offerings = offerings;
        this.rooms = rooms;
        this.classrooms = classrooms;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        TenantDirectory.TenantSummary tenant =
                tenantDirectory
                        .findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"));
        if (organizations.findByTenantId(tenant.id()).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        Organization organization =
                organizations.save(
                        Organization.create(
                                tenant.id(),
                                new Organization.Details(
                                        "SCHOOLZ-DEMO",
                                        "Groupe Scolaire SchoolZ",
                                        "Groupe Scolaire SchoolZ SARL",
                                        null,
                                        "12 boulevard Zerktouni",
                                        null,
                                        "Casablanca",
                                        "Casablanca-Settat",
                                        "20000",
                                        "MA",
                                        "+212 5 22 00 00 00",
                                        "contact@schoolz.ma"),
                                "{\"ICE\":\"001234567890123\"}",
                                now));
        settings.save(
                SchoolSettings.create(
                        tenant.id(),
                        organization.id(),
                        new SchoolSettings.Values(
                                "fr-MA",
                                "ar-MA,fr-MA",
                                "MAD",
                                "Africa/Casablanca",
                                1,
                                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                                LocalTime.of(8, 0),
                                LocalTime.of(17, 30),
                                "Calendrier de démonstration configurable"),
                        now));

        Campus centre =
                campuses.save(
                        Campus.create(
                                tenant.id(),
                                organization.id(),
                                campus(
                                        "CASA-CENTRE",
                                        "Campus Casablanca Centre",
                                        "12 boulevard Zerktouni",
                                        "Casablanca"),
                                now));
        Campus bouskoura =
                campuses.save(
                        Campus.create(
                                tenant.id(),
                                organization.id(),
                                campus(
                                        "BOUSKOURA",
                                        "Campus Bouskoura",
                                        "Route de Bouskoura",
                                        "Bouskoura"),
                                now));

        AcademicYear year =
                AcademicYear.create(
                        tenant.id(),
                        organization.id(),
                        "2026-2027",
                        "Année scolaire 2026–2027",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 6, 30),
                        now);
        year.changeStatus(AcademicYearStatus.ACTIVE, now);
        years.save(year);
        terms.saveAll(
                List.of(
                        AcademicTerm.create(
                                tenant.id(),
                                year.id(),
                                "T1",
                                "Trimestre 1",
                                TermType.TRIMESTER,
                                1,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 12, 19),
                                now),
                        AcademicTerm.create(
                                tenant.id(),
                                year.id(),
                                "T2",
                                "Trimestre 2",
                                TermType.TRIMESTER,
                                2,
                                LocalDate.of(2027, 1, 4),
                                LocalDate.of(2027, 3, 27),
                                now),
                        AcademicTerm.create(
                                tenant.id(),
                                year.id(),
                                "T3",
                                "Trimestre 3",
                                TermType.TRIMESTER,
                                3,
                                LocalDate.of(2027, 4, 5),
                                LocalDate.of(2027, 6, 30),
                                now)));

        SchoolLevel preschool =
                levels.save(
                        SchoolLevel.create(
                                tenant.id(),
                                organization.id(),
                                "MATERNELLE",
                                "Maternelle",
                                1,
                                now));
        SchoolLevel primary =
                levels.save(
                        SchoolLevel.create(
                                tenant.id(), organization.id(), "PRIMAIRE", "Primaire", 2, now));
        SchoolLevel middle =
                levels.save(
                        SchoolLevel.create(
                                tenant.id(), organization.id(), "COLLEGE", "Collège", 3, now));
        GradeLevel ms =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), preschool.id(), "MS", "Moyenne section", 1, now));
        GradeLevel gs =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), preschool.id(), "GS", "Grande section", 2, now));
        GradeLevel cp =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), primary.id(), "1AP", "1re année primaire", 3, now));
        GradeLevel ce1 =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), primary.id(), "2AP", "2e année primaire", 4, now));
        GradeLevel sixth =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), primary.id(), "6AP", "6e année primaire", 8, now));
        GradeLevel middleOne =
                grades.save(
                        GradeLevel.create(
                                tenant.id(), middle.id(), "1AC", "1re année collège", 9, now));

        offerings.saveAll(
                List.of(
                        offer(tenant.id(), centre.id(), ms.id(), now),
                        offer(tenant.id(), centre.id(), gs.id(), now),
                        offer(tenant.id(), centre.id(), cp.id(), now),
                        offer(tenant.id(), centre.id(), ce1.id(), now),
                        offer(tenant.id(), centre.id(), sixth.id(), now),
                        offer(tenant.id(), centre.id(), middleOne.id(), now),
                        offer(tenant.id(), bouskoura.id(), ms.id(), now),
                        offer(tenant.id(), bouskoura.id(), gs.id(), now),
                        offer(tenant.id(), bouskoura.id(), cp.id(), now),
                        offer(tenant.id(), bouskoura.id(), ce1.id(), now)));

        PhysicalRoom roomA =
                rooms.save(
                        PhysicalRoom.create(
                                tenant.id(),
                                centre.id(),
                                "A-101",
                                "Salle A-101",
                                "CLASSROOM",
                                28,
                                "1",
                                now));
        PhysicalRoom roomB =
                rooms.save(
                        PhysicalRoom.create(
                                tenant.id(),
                                centre.id(),
                                "LAB-SCI",
                                "Laboratoire sciences",
                                "LAB",
                                24,
                                "2",
                                now));
        PhysicalRoom roomC =
                rooms.save(
                        PhysicalRoom.create(
                                tenant.id(),
                                bouskoura.id(),
                                "B-001",
                                "Salle B-001",
                                "CLASSROOM",
                                26,
                                "RDC",
                                now));
        classrooms.saveAll(
                List.of(
                        Classroom.create(
                                tenant.id(),
                                centre.id(),
                                year.id(),
                                cp.id(),
                                roomA.id(),
                                "1AP-A",
                                "1AP — Groupe A",
                                26,
                                now),
                        Classroom.create(
                                tenant.id(),
                                centre.id(),
                                year.id(),
                                middleOne.id(),
                                roomB.id(),
                                "1AC-A",
                                "1AC — Groupe A",
                                24,
                                now),
                        Classroom.create(
                                tenant.id(),
                                bouskoura.id(),
                                year.id(),
                                ce1.id(),
                                roomC.id(),
                                "2AP-B",
                                "2AP — Groupe B",
                                25,
                                now)));
    }

    private static Campus.Details campus(String code, String name, String address, String city) {
        return new Campus.Details(
                code,
                name,
                address,
                null,
                city,
                "Casablanca-Settat",
                "20000",
                "MA",
                "+212 5 22 00 00 00",
                "contact@schoolz.ma",
                "fr-MA",
                "Africa/Casablanca");
    }

    private static CampusGradeOffering offer(
            java.util.UUID tenantId, java.util.UUID campusId, java.util.UUID gradeId, Instant now) {
        return CampusGradeOffering.create(tenantId, campusId, gradeId, now);
    }
}
