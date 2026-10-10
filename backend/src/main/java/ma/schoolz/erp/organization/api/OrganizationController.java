package ma.schoolz.erp.organization.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.organization.application.OrganizationAdministrationService;
import ma.schoolz.erp.organization.domain.AcademicYearStatus;
import ma.schoolz.erp.organization.domain.TermType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

    private final OrganizationAdministrationService administration;

    public OrganizationController(OrganizationAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/organization")
    public OrganizationAdministrationService.OrganizationView organization(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.organization(principal);
    }

    @PutMapping("/organization")
    public OrganizationAdministrationService.OrganizationView configureOrganization(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody OrganizationRequest request,
            HttpServletRequest servletRequest) {
        return administration.configureOrganization(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/organization/settings")
    public OrganizationAdministrationService.SettingsView settings(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.settings(principal);
    }

    @PutMapping("/organization/settings")
    public OrganizationAdministrationService.SettingsView configureSettings(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody SettingsRequest request,
            HttpServletRequest servletRequest) {
        return administration.configureSettings(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/campuses")
    public List<OrganizationAdministrationService.CampusView> campuses(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.campuses(principal);
    }

    @PostMapping("/campuses")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.CampusView createCampus(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody CampusRequest request,
            HttpServletRequest servletRequest) {
        return administration.createCampus(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @PutMapping("/campuses/{campusId}")
    public OrganizationAdministrationService.CampusView updateCampus(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID campusId,
            @Valid @RequestBody CampusRequest request,
            HttpServletRequest servletRequest) {
        return administration.updateCampus(
                principal, campusId, request.command(), servletRequest.getRemoteAddr());
    }

    @PutMapping("/campuses/{campusId}/grade-levels")
    public OrganizationAdministrationService.CampusView setCampusOfferings(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID campusId,
            @Valid @RequestBody GradeOfferingsRequest request,
            HttpServletRequest servletRequest) {
        return administration.setCampusOfferings(
                principal, campusId, request.gradeLevelIds(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/academics/years")
    public List<OrganizationAdministrationService.AcademicYearView> years(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.academicYears(principal);
    }

    @PostMapping("/academics/years")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.AcademicYearView createYear(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody AcademicYearRequest request,
            HttpServletRequest servletRequest) {
        return administration.createAcademicYear(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @PatchMapping("/academics/years/{yearId}/status")
    public OrganizationAdministrationService.AcademicYearView changeYearStatus(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID yearId,
            @Valid @RequestBody YearStatusRequest request,
            HttpServletRequest servletRequest) {
        return administration.changeAcademicYearStatus(
                principal, yearId, request.status(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/academics/years/{yearId}/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.AcademicTermView createTerm(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID yearId,
            @Valid @RequestBody TermRequest request,
            HttpServletRequest servletRequest) {
        return administration.createTerm(
                principal, yearId, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/academics/levels")
    public List<OrganizationAdministrationService.SchoolLevelView> levels(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.schoolLevels(principal);
    }

    @PostMapping("/academics/levels")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.SchoolLevelView createLevel(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody LevelRequest request,
            HttpServletRequest servletRequest) {
        return administration.createSchoolLevel(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/academics/levels/{levelId}/grades")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.GradeLevelView createGrade(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID levelId,
            @Valid @RequestBody LevelRequest request,
            HttpServletRequest servletRequest) {
        return administration.createGradeLevel(
                principal, levelId, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/academics/rooms")
    public List<OrganizationAdministrationService.PhysicalRoomView> rooms(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.rooms(principal);
    }

    @PostMapping("/academics/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.PhysicalRoomView createRoom(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody RoomRequest request,
            HttpServletRequest servletRequest) {
        return administration.createRoom(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/academics/classrooms")
    public List<OrganizationAdministrationService.ClassroomView> classrooms(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.classrooms(principal);
    }

    @PostMapping("/academics/classrooms")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationAdministrationService.ClassroomView createClassroom(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody ClassroomRequest request,
            HttpServletRequest servletRequest) {
        return administration.createClassroom(
                principal, request.command(), servletRequest.getRemoteAddr());
    }

    public record OrganizationRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 220) String legalName,
            @Size(max = 500) String logoObjectKey,
            @Size(max = 220) String addressLine1,
            @Size(max = 220) String addressLine2,
            @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @NotNull Map<@Size(max = 40) String, @Size(max = 120) String> businessIdentifiers) {
        OrganizationAdministrationService.OrganizationCommand command() {
            return new OrganizationAdministrationService.OrganizationCommand(
                    code,
                    name,
                    legalName,
                    logoObjectKey,
                    addressLine1,
                    addressLine2,
                    city,
                    region,
                    postalCode,
                    countryCode,
                    phone,
                    email,
                    businessIdentifiers);
        }
    }

    public record SettingsRequest(
            @NotBlank @Pattern(regexp = "fr-MA|ar-MA") String defaultLocale,
            @NotEmpty Set<@Pattern(regexp = "fr-MA|ar-MA") String> supportedLocales,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
            @NotBlank @Size(max = 80) String timezone,
            @Min(1) @Max(7) int weekStartsOn,
            @NotEmpty Set<DayOfWeek> workingDays,
            @NotNull LocalTime dayStartsAt,
            @NotNull LocalTime dayEndsAt,
            @Size(max = 500) String calendarNotes) {
        OrganizationAdministrationService.SettingsCommand command() {
            return new OrganizationAdministrationService.SettingsCommand(
                    defaultLocale,
                    supportedLocales,
                    currencyCode,
                    timezone,
                    weekStartsOn,
                    workingDays,
                    dayStartsAt,
                    dayEndsAt,
                    calendarNotes);
        }
    }

    public record CampusRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 220) String addressLine1,
            @Size(max = 220) String addressLine2,
            @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "fr-MA|ar-MA") String locale,
            @NotBlank @Size(max = 80) String timezone) {
        OrganizationAdministrationService.CampusCommand command() {
            return new OrganizationAdministrationService.CampusCommand(
                    code,
                    name,
                    addressLine1,
                    addressLine2,
                    city,
                    region,
                    postalCode,
                    countryCode,
                    phone,
                    email,
                    locale,
                    timezone);
        }
    }

    public record GradeOfferingsRequest(@NotNull Set<UUID> gradeLevelIds) {}

    public record AcademicYearRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 140) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {
        OrganizationAdministrationService.AcademicYearCommand command() {
            return new OrganizationAdministrationService.AcademicYearCommand(
                    code, name, startsOn, endsOn);
        }
    }

    public record YearStatusRequest(@NotNull AcademicYearStatus status) {}

    public record TermRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 140) String name,
            @NotNull TermType type,
            @Positive int sequence,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {
        OrganizationAdministrationService.TermCommand command() {
            return new OrganizationAdministrationService.TermCommand(
                    code, name, type, sequence, startsOn, endsOn);
        }
    }

    public record LevelRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 140) String name,
            @Positive int sequence) {
        OrganizationAdministrationService.LevelCommand command() {
            return new OrganizationAdministrationService.LevelCommand(code, name, sequence);
        }
    }

    public record RoomRequest(
            @NotNull UUID campusId,
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 140) String name,
            @NotBlank @Size(max = 40) String roomType,
            @Positive int capacity,
            @Size(max = 40) String floorLabel) {
        OrganizationAdministrationService.RoomCommand command() {
            return new OrganizationAdministrationService.RoomCommand(
                    campusId, code, name, roomType, capacity, floorLabel);
        }
    }

    public record ClassroomRequest(
            @NotNull UUID campusId,
            @NotNull UUID academicYearId,
            @NotNull UUID gradeLevelId,
            UUID physicalRoomId,
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 140) String name,
            @Positive int capacity) {
        OrganizationAdministrationService.ClassroomCommand command() {
            return new OrganizationAdministrationService.ClassroomCommand(
                    campusId, academicYearId, gradeLevelId, physicalRoomId, code, name, capacity);
        }
    }
}
