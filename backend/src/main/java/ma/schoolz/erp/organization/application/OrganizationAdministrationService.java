package ma.schoolz.erp.organization.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.organization.domain.AcademicTerm;
import ma.schoolz.erp.organization.domain.AcademicYear;
import ma.schoolz.erp.organization.domain.AcademicYearStatus;
import ma.schoolz.erp.organization.domain.Campus;
import ma.schoolz.erp.organization.domain.CampusGradeOffering;
import ma.schoolz.erp.organization.domain.Classroom;
import ma.schoolz.erp.organization.domain.GradeLevel;
import ma.schoolz.erp.organization.domain.Organization;
import ma.schoolz.erp.organization.domain.OrganizationCode;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAdministrationService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("fr-MA", "ar-MA");
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
    private final IdentityAuditTrail audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrganizationAdministrationService(
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
            IdentityAuditTrail audit,
            ObjectMapper objectMapper,
            Clock clock) {
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
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('organization.read')")
    public OrganizationView organization(SchoolPrincipal actor) {
        return organizationView(requireOrganization(actor.tenantId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('organization.write')")
    public OrganizationView configureOrganization(
            SchoolPrincipal actor, OrganizationCommand command, String ipAddress) {
        validateCountry(command.countryCode());
        Map<String, String> identifiers = normalizeIdentifiers(command.businessIdentifiers());
        String serialized = json(identifiers);
        Instant now = clock.instant();
        Organization existing = organizations.findByTenantId(actor.tenantId()).orElse(null);
        OrganizationView before = existing == null ? null : organizationView(existing);
        Organization organization;
        if (existing == null) {
            organization =
                    Organization.create(
                            actor.tenantId(), organizationDetails(command), serialized, now);
        } else {
            existing.update(organizationDetails(command), serialized, now);
            organization = existing;
        }
        organizations.save(organization);
        OrganizationView after = organizationView(organization);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                existing == null ? "organization.created" : "organization.updated",
                "Organization",
                organization.id().toString(),
                ipAddress,
                Map.of(),
                before,
                after);
        return after;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('organization.read')")
    public SettingsView settings(SchoolPrincipal actor) {
        return settings.findByTenantId(actor.tenantId())
                .map(OrganizationAdministrationService::settingsView)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    @Transactional
    @PreAuthorize("hasAuthority('organization.write')")
    public SettingsView configureSettings(
            SchoolPrincipal actor, SettingsCommand command, String ipAddress) {
        validateSettings(command);
        Organization organization = requireOrganization(actor.tenantId());
        SchoolSettings current = settings.findByTenantId(actor.tenantId()).orElse(null);
        SettingsView before = current == null ? null : settingsView(current);
        SchoolSettings.Values values =
                new SchoolSettings.Values(
                        command.defaultLocale(),
                        join(command.supportedLocales()),
                        command.currencyCode().toUpperCase(Locale.ROOT),
                        command.timezone(),
                        command.weekStartsOn(),
                        joinDays(command.workingDays()),
                        command.dayStartsAt(),
                        command.dayEndsAt(),
                        blankToNull(command.calendarNotes()));
        Instant now = clock.instant();
        if (current == null) {
            current = SchoolSettings.create(actor.tenantId(), organization.id(), values, now);
        } else {
            current.update(values, now);
        }
        settings.save(current);
        SettingsView after = settingsView(current);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "organization.settings_updated",
                "SchoolSettings",
                current.id().toString(),
                ipAddress,
                Map.of(),
                before,
                after);
        return after;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('campus.read')")
    public List<CampusView> campuses(SchoolPrincipal actor) {
        return campuses.findAllByTenantIdOrderByNameAsc(actor.tenantId()).stream()
                .map(campus -> campusView(campus, offeringIds(actor.tenantId(), campus.id())))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('campus.write')")
    public CampusView createCampus(SchoolPrincipal actor, CampusCommand command, String ipAddress) {
        validateCampus(command);
        String code = OrganizationCode.normalize(command.code());
        if (campuses.existsByTenantIdAndCode(actor.tenantId(), code)) {
            throw new OrganizationConflictException("Campus code already exists");
        }
        Organization organization = requireOrganization(actor.tenantId());
        Campus campus =
                Campus.create(
                        actor.tenantId(),
                        organization.id(),
                        campusDetails(command),
                        clock.instant());
        campuses.save(campus);
        CampusView view = campusView(campus, Set.of());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "campus.created",
                "Campus",
                campus.id().toString(),
                ipAddress,
                Map.of(),
                null,
                view);
        return view;
    }

    @Transactional
    @PreAuthorize("hasAuthority('campus.write')")
    public CampusView updateCampus(
            SchoolPrincipal actor, UUID campusId, CampusCommand command, String ipAddress) {
        validateCampus(command);
        Campus campus = requireCampus(actor.tenantId(), campusId);
        CampusView before = campusView(campus, offeringIds(actor.tenantId(), campus.id()));
        campus.update(campusDetails(command), clock.instant());
        campuses.save(campus);
        CampusView after = campusView(campus, before.offeredGradeLevelIds());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "campus.updated",
                "Campus",
                campus.id().toString(),
                ipAddress,
                Map.of(),
                before,
                after);
        return after;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('academic-structure.read')")
    public List<AcademicYearView> academicYears(SchoolPrincipal actor) {
        return years.findAllByTenantIdOrderByStartsOnDesc(actor.tenantId()).stream()
                .map(
                        year ->
                                academicYearView(
                                        year,
                                        terms.findAllByTenantIdAndAcademicYearIdOrderBySequenceAsc(
                                                actor.tenantId(), year.id())))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public AcademicYearView createAcademicYear(
            SchoolPrincipal actor, AcademicYearCommand command, String ipAddress) {
        requireDateRange(command.startsOn(), command.endsOn(), "Academic year");
        String code = OrganizationCode.normalize(command.code());
        if (years.existsByTenantIdAndCode(actor.tenantId(), code)) {
            throw new OrganizationConflictException("Academic year code already exists");
        }
        Organization organization = requireOrganization(actor.tenantId());
        AcademicYear year =
                AcademicYear.create(
                        actor.tenantId(),
                        organization.id(),
                        code,
                        command.name(),
                        command.startsOn(),
                        command.endsOn(),
                        clock.instant());
        years.save(year);
        AcademicYearView view = academicYearView(year, List.of());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.year_created",
                "AcademicYear",
                year.id().toString(),
                ipAddress,
                Map.of(),
                null,
                view);
        return view;
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public AcademicYearView changeAcademicYearStatus(
            SchoolPrincipal actor, UUID yearId, AcademicYearStatus status, String ipAddress) {
        AcademicYear year = requireYear(actor.tenantId(), yearId);
        AcademicYearStatus before = year.status();
        if (status == AcademicYearStatus.ACTIVE) {
            years.findAllByTenantIdAndStatus(actor.tenantId(), AcademicYearStatus.ACTIVE).stream()
                    .filter(active -> !active.id().equals(year.id()))
                    .forEach(
                            active ->
                                    active.changeStatus(
                                            AcademicYearStatus.CLOSED, clock.instant()));
        }
        year.changeStatus(status, clock.instant());
        years.save(year);
        List<AcademicTerm> yearTerms =
                terms.findAllByTenantIdAndAcademicYearIdOrderBySequenceAsc(
                        actor.tenantId(), year.id());
        AcademicYearView after = academicYearView(year, yearTerms);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.year_status_changed",
                "AcademicYear",
                year.id().toString(),
                ipAddress,
                Map.of(),
                Map.of("status", before),
                Map.of("status", status));
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public AcademicTermView createTerm(
            SchoolPrincipal actor, UUID yearId, TermCommand command, String ipAddress) {
        AcademicYear year = requireYear(actor.tenantId(), yearId);
        requireDateRange(command.startsOn(), command.endsOn(), "Term");
        if (command.startsOn().isBefore(year.startsOn())
                || command.endsOn().isAfter(year.endsOn())) {
            throw new OrganizationValidationException("Term must fit within academic year");
        }
        String code = OrganizationCode.normalize(command.code());
        if (terms.existsByTenantIdAndAcademicYearIdAndCode(actor.tenantId(), yearId, code)) {
            throw new OrganizationConflictException("Term code already exists");
        }
        if (terms
                .existsByTenantIdAndAcademicYearIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
                        actor.tenantId(), yearId, command.endsOn(), command.startsOn())) {
            throw new OrganizationConflictException("Term dates overlap an existing term");
        }
        AcademicTerm term =
                AcademicTerm.create(
                        actor.tenantId(),
                        yearId,
                        code,
                        command.name(),
                        command.type(),
                        command.sequence(),
                        command.startsOn(),
                        command.endsOn(),
                        clock.instant());
        terms.save(term);
        AcademicTermView view = termView(term);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.term_created",
                "AcademicTerm",
                term.id().toString(),
                ipAddress,
                Map.of("academicYearId", yearId),
                null,
                view);
        return view;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('academic-structure.read')")
    public List<SchoolLevelView> schoolLevels(SchoolPrincipal actor) {
        return levels.findAllByTenantIdOrderBySequenceAsc(actor.tenantId()).stream()
                .map(
                        level ->
                                schoolLevelView(
                                        level,
                                        grades.findAllByTenantIdAndSchoolLevelIdOrderBySequenceAsc(
                                                actor.tenantId(), level.id())))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public SchoolLevelView createSchoolLevel(
            SchoolPrincipal actor, LevelCommand command, String ipAddress) {
        requirePositive(command.sequence(), "Sequence");
        String code = OrganizationCode.normalize(command.code());
        if (levels.existsByTenantIdAndCode(actor.tenantId(), code))
            throw new OrganizationConflictException("School level code already exists");
        SchoolLevel level =
                SchoolLevel.create(
                        actor.tenantId(),
                        requireOrganization(actor.tenantId()).id(),
                        code,
                        command.name(),
                        command.sequence(),
                        clock.instant());
        levels.save(level);
        SchoolLevelView view = schoolLevelView(level, List.of());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.school_level_created",
                "SchoolLevel",
                level.id().toString(),
                ipAddress,
                Map.of(),
                null,
                view);
        return view;
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public GradeLevelView createGradeLevel(
            SchoolPrincipal actor, UUID schoolLevelId, LevelCommand command, String ipAddress) {
        requirePositive(command.sequence(), "Sequence");
        SchoolLevel level =
                levels.findByIdAndTenantId(schoolLevelId, actor.tenantId())
                        .orElseThrow(OrganizationNotFoundException::new);
        String code = OrganizationCode.normalize(command.code());
        if (grades.existsByTenantIdAndCode(actor.tenantId(), code))
            throw new OrganizationConflictException("Grade level code already exists");
        GradeLevel grade =
                GradeLevel.create(
                        actor.tenantId(),
                        level.id(),
                        code,
                        command.name(),
                        command.sequence(),
                        clock.instant());
        grades.save(grade);
        GradeLevelView view = gradeView(grade);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.grade_level_created",
                "GradeLevel",
                grade.id().toString(),
                ipAddress,
                Map.of("schoolLevelId", schoolLevelId),
                null,
                view);
        return view;
    }

    @Transactional
    @PreAuthorize("hasAuthority('campus.write')")
    public CampusView setCampusOfferings(
            SchoolPrincipal actor, UUID campusId, Set<UUID> gradeLevelIds, String ipAddress) {
        Campus campus = requireCampus(actor.tenantId(), campusId);
        List<GradeLevel> selected =
                grades.findAllByTenantIdAndIdIn(actor.tenantId(), gradeLevelIds);
        if (selected.size() != gradeLevelIds.size()) throw new OrganizationNotFoundException();
        Set<UUID> before = offeringIds(actor.tenantId(), campusId);
        offerings.deleteAllByTenantIdAndCampusId(actor.tenantId(), campusId);
        offerings.flush();
        Instant now = clock.instant();
        offerings.saveAll(
                gradeLevelIds.stream()
                        .map(id -> CampusGradeOffering.create(actor.tenantId(), campusId, id, now))
                        .toList());
        CampusView after = campusView(campus, Set.copyOf(gradeLevelIds));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "campus.grade_offerings_changed",
                "Campus",
                campus.id().toString(),
                ipAddress,
                Map.of(),
                Map.of("gradeLevelIds", before),
                Map.of("gradeLevelIds", gradeLevelIds));
        return after;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('academic-structure.read')")
    public List<PhysicalRoomView> rooms(SchoolPrincipal actor) {
        return rooms.findAllByTenantIdOrderByNameAsc(actor.tenantId()).stream()
                .map(OrganizationAdministrationService::roomView)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public PhysicalRoomView createRoom(
            SchoolPrincipal actor, RoomCommand command, String ipAddress) {
        requireCampus(actor.tenantId(), command.campusId());
        requirePositive(command.capacity(), "Capacity");
        String code = OrganizationCode.normalize(command.code());
        if (rooms.existsByTenantIdAndCampusIdAndCode(actor.tenantId(), command.campusId(), code))
            throw new OrganizationConflictException("Room code already exists in campus");
        PhysicalRoom room =
                PhysicalRoom.create(
                        actor.tenantId(),
                        command.campusId(),
                        code,
                        command.name(),
                        command.roomType(),
                        command.capacity(),
                        command.floorLabel(),
                        clock.instant());
        rooms.save(room);
        PhysicalRoomView view = roomView(room);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.room_created",
                "PhysicalRoom",
                room.id().toString(),
                ipAddress,
                Map.of("campusId", command.campusId()),
                null,
                view);
        return view;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('academic-structure.read')")
    public List<ClassroomView> classrooms(SchoolPrincipal actor) {
        return classrooms.findAllByTenantIdOrderByNameAsc(actor.tenantId()).stream()
                .map(OrganizationAdministrationService::classroomView)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('academic-structure.write')")
    public ClassroomView createClassroom(
            SchoolPrincipal actor, ClassroomCommand command, String ipAddress) {
        requireCampus(actor.tenantId(), command.campusId());
        requireYear(actor.tenantId(), command.academicYearId());
        grades.findByIdAndTenantId(command.gradeLevelId(), actor.tenantId())
                .orElseThrow(OrganizationNotFoundException::new);
        if (!offerings.existsByTenantIdAndCampusIdAndGradeLevelId(
                actor.tenantId(), command.campusId(), command.gradeLevelId()))
            throw new OrganizationValidationException("Grade level is not offered by this campus");
        if (command.physicalRoomId() != null) {
            PhysicalRoom room =
                    rooms.findByIdAndTenantId(command.physicalRoomId(), actor.tenantId())
                            .orElseThrow(OrganizationNotFoundException::new);
            if (!room.campusId().equals(command.campusId()))
                throw new OrganizationValidationException("Room belongs to another campus");
        }
        requirePositive(command.capacity(), "Capacity");
        String code = OrganizationCode.normalize(command.code());
        if (classrooms.existsByTenantIdAndAcademicYearIdAndCampusIdAndCode(
                actor.tenantId(), command.academicYearId(), command.campusId(), code))
            throw new OrganizationConflictException(
                    "Classroom code already exists for this campus and year");
        Classroom classroom =
                Classroom.create(
                        actor.tenantId(),
                        command.campusId(),
                        command.academicYearId(),
                        command.gradeLevelId(),
                        command.physicalRoomId(),
                        code,
                        command.name(),
                        command.capacity(),
                        clock.instant());
        classrooms.save(classroom);
        ClassroomView view = classroomView(classroom);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "academic.classroom_created",
                "Classroom",
                classroom.id().toString(),
                ipAddress,
                Map.of("campusId", command.campusId()),
                null,
                view);
        return view;
    }

    private Organization requireOrganization(UUID tenantId) {
        return organizations
                .findByTenantId(tenantId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private Campus requireCampus(UUID tenantId, UUID campusId) {
        return campuses.findByIdAndTenantId(campusId, tenantId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private AcademicYear requireYear(UUID tenantId, UUID yearId) {
        return years.findByIdAndTenantId(yearId, tenantId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private Set<UUID> offeringIds(UUID tenantId, UUID campusId) {
        return offerings.findAllByTenantIdAndCampusId(tenantId, campusId).stream()
                .map(CampusGradeOffering::gradeLevelId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validateSettings(SettingsCommand command) {
        if (!SUPPORTED_LOCALES.contains(command.defaultLocale())
                || command.supportedLocales().isEmpty()
                || !SUPPORTED_LOCALES.containsAll(command.supportedLocales())
                || !command.supportedLocales().contains(command.defaultLocale()))
            throw new OrganizationValidationException("Unsupported locale configuration");
        if (command.currencyCode() == null || !command.currencyCode().matches("[A-Za-z]{3}"))
            throw new OrganizationValidationException("Currency must be an ISO 4217 code");
        try {
            ZoneId.of(command.timezone());
        } catch (Exception exception) {
            throw new OrganizationValidationException("Unknown timezone");
        }
        if (command.weekStartsOn() < 1
                || command.weekStartsOn() > 7
                || command.workingDays().isEmpty())
            throw new OrganizationValidationException("Invalid school week");
        if (command.dayStartsAt() == null
                || command.dayEndsAt() == null
                || !command.dayStartsAt().isBefore(command.dayEndsAt()))
            throw new OrganizationValidationException("School start time must precede end time");
    }

    private void validateCampus(CampusCommand command) {
        validateCountry(command.countryCode());
        if (!SUPPORTED_LOCALES.contains(command.locale()))
            throw new OrganizationValidationException("Unsupported locale");
        try {
            ZoneId.of(command.timezone());
        } catch (Exception exception) {
            throw new OrganizationValidationException("Unknown timezone");
        }
    }

    private static void validateCountry(String value) {
        if (value == null || !value.matches("[A-Za-z]{2}"))
            throw new OrganizationValidationException("Country must be an ISO alpha-2 code");
    }

    private static void requireDateRange(LocalDate start, LocalDate end, String label) {
        if (start == null || end == null || !start.isBefore(end))
            throw new OrganizationValidationException(label + " start date must precede end date");
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) throw new OrganizationValidationException(label + " must be positive");
    }

    private Map<String, String> normalizeIdentifiers(Map<String, String> values) {
        if (values == null) return Map.of();
        if (values.size() > 20)
            throw new OrganizationValidationException("Too many business identifiers");
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    String normalized = OrganizationCode.normalize(key);
                    if (normalized.isBlank()
                            || normalized.length() > 40
                            || value == null
                            || value.isBlank()
                            || value.strip().length() > 120)
                        throw new OrganizationValidationException("Invalid business identifier");
                    result.put(normalized, value.strip());
                });
        return Map.copyOf(result);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize organization data", exception);
        }
    }

    private Map<String, String> identifiers(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read organization identifiers", exception);
        }
    }

    private static String join(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    private static String joinDays(Set<DayOfWeek> values) {
        return values.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<String> split(String value) {
        return Set.of(value.split(","));
    }

    private static Set<DayOfWeek> splitDays(String value) {
        return java.util.Arrays.stream(value.split(","))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Organization.Details organizationDetails(OrganizationCommand c) {
        return new Organization.Details(
                c.code(),
                c.name(),
                c.legalName(),
                c.logoObjectKey(),
                c.addressLine1(),
                c.addressLine2(),
                c.city(),
                c.region(),
                c.postalCode(),
                c.countryCode(),
                c.phone(),
                c.email());
    }

    private static Campus.Details campusDetails(CampusCommand c) {
        return new Campus.Details(
                c.code(),
                c.name(),
                c.addressLine1(),
                c.addressLine2(),
                c.city(),
                c.region(),
                c.postalCode(),
                c.countryCode(),
                c.phone(),
                c.email(),
                c.locale(),
                c.timezone());
    }

    private OrganizationView organizationView(Organization o) {
        return new OrganizationView(
                o.id(),
                o.code(),
                o.name(),
                o.legalName(),
                o.logoObjectKey(),
                o.addressLine1(),
                o.addressLine2(),
                o.city(),
                o.region(),
                o.postalCode(),
                o.countryCode(),
                o.phone(),
                o.email(),
                identifiers(o.businessIdentifiers()),
                o.createdAt(),
                o.updatedAt());
    }

    private static SettingsView settingsView(SchoolSettings s) {
        return new SettingsView(
                s.id(),
                s.defaultLocale(),
                split(s.supportedLocales()),
                s.currencyCode(),
                s.timezone(),
                s.weekStartsOn(),
                splitDays(s.workingDays()),
                s.dayStartsAt(),
                s.dayEndsAt(),
                s.calendarNotes(),
                s.updatedAt());
    }

    private static CampusView campusView(Campus c, Set<UUID> offered) {
        return new CampusView(
                c.id(),
                c.code(),
                c.name(),
                c.addressLine1(),
                c.addressLine2(),
                c.city(),
                c.region(),
                c.postalCode(),
                c.countryCode(),
                c.phone(),
                c.email(),
                c.locale(),
                c.timezone(),
                c.active(),
                offered,
                c.createdAt(),
                c.updatedAt());
    }

    private static AcademicYearView academicYearView(AcademicYear y, List<AcademicTerm> terms) {
        return new AcademicYearView(
                y.id(),
                y.code(),
                y.name(),
                y.startsOn(),
                y.endsOn(),
                y.status(),
                terms.stream().map(OrganizationAdministrationService::termView).toList(),
                y.createdAt(),
                y.updatedAt());
    }

    private static AcademicTermView termView(AcademicTerm t) {
        return new AcademicTermView(
                t.id(),
                t.academicYearId(),
                t.code(),
                t.name(),
                t.type(),
                t.sequence(),
                t.startsOn(),
                t.endsOn());
    }

    private static SchoolLevelView schoolLevelView(SchoolLevel l, List<GradeLevel> grades) {
        return new SchoolLevelView(
                l.id(),
                l.code(),
                l.name(),
                l.sequence(),
                l.active(),
                grades.stream().map(OrganizationAdministrationService::gradeView).toList());
    }

    private static GradeLevelView gradeView(GradeLevel g) {
        return new GradeLevelView(
                g.id(), g.schoolLevelId(), g.code(), g.name(), g.sequence(), g.active());
    }

    private static PhysicalRoomView roomView(PhysicalRoom r) {
        return new PhysicalRoomView(
                r.id(),
                r.campusId(),
                r.code(),
                r.name(),
                r.roomType(),
                r.capacity(),
                r.floorLabel(),
                r.active());
    }

    private static ClassroomView classroomView(Classroom c) {
        return new ClassroomView(
                c.id(),
                c.campusId(),
                c.academicYearId(),
                c.gradeLevelId(),
                c.physicalRoomId(),
                c.code(),
                c.name(),
                c.capacity(),
                c.active());
    }

    public record OrganizationCommand(
            String code,
            String name,
            String legalName,
            String logoObjectKey,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            Map<String, String> businessIdentifiers) {}

    public record SettingsCommand(
            String defaultLocale,
            Set<String> supportedLocales,
            String currencyCode,
            String timezone,
            int weekStartsOn,
            Set<DayOfWeek> workingDays,
            LocalTime dayStartsAt,
            LocalTime dayEndsAt,
            String calendarNotes) {}

    public record CampusCommand(
            String code,
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            String locale,
            String timezone) {}

    public record AcademicYearCommand(
            String code, String name, LocalDate startsOn, LocalDate endsOn) {}

    public record TermCommand(
            String code,
            String name,
            TermType type,
            int sequence,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record LevelCommand(String code, String name, int sequence) {}

    public record RoomCommand(
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel) {}

    public record ClassroomCommand(
            UUID campusId,
            UUID academicYearId,
            UUID gradeLevelId,
            UUID physicalRoomId,
            String code,
            String name,
            int capacity) {}

    public record OrganizationView(
            UUID id,
            String code,
            String name,
            String legalName,
            String logoObjectKey,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            Map<String, String> businessIdentifiers,
            Instant createdAt,
            Instant updatedAt) {}

    public record SettingsView(
            UUID id,
            String defaultLocale,
            Set<String> supportedLocales,
            String currencyCode,
            String timezone,
            int weekStartsOn,
            Set<DayOfWeek> workingDays,
            LocalTime dayStartsAt,
            LocalTime dayEndsAt,
            String calendarNotes,
            Instant updatedAt) {}

    public record CampusView(
            UUID id,
            String code,
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            String locale,
            String timezone,
            boolean active,
            Set<UUID> offeredGradeLevelIds,
            Instant createdAt,
            Instant updatedAt) {}

    public record AcademicYearView(
            UUID id,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            AcademicYearStatus status,
            List<AcademicTermView> terms,
            Instant createdAt,
            Instant updatedAt) {}

    public record AcademicTermView(
            UUID id,
            UUID academicYearId,
            String code,
            String name,
            TermType type,
            int sequence,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record SchoolLevelView(
            UUID id,
            String code,
            String name,
            int sequence,
            boolean active,
            List<GradeLevelView> grades) {}

    public record GradeLevelView(
            UUID id, UUID schoolLevelId, String code, String name, int sequence, boolean active) {}

    public record PhysicalRoomView(
            UUID id,
            UUID campusId,
            String code,
            String name,
            String roomType,
            int capacity,
            String floorLabel,
            boolean active) {}

    public record ClassroomView(
            UUID id,
            UUID campusId,
            UUID academicYearId,
            UUID gradeLevelId,
            UUID physicalRoomId,
            String code,
            String name,
            int capacity,
            boolean active) {}
}
